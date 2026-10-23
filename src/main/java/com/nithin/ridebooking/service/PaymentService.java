package com.nithin.ridebooking.service;

import com.nithin.ridebooking.dto.PaymentResponse;
import com.nithin.ridebooking.entity.*;
import com.nithin.ridebooking.exception.BadRequestException;
import com.nithin.ridebooking.exception.ResourceNotFoundException;
import com.nithin.ridebooking.payment.PaymentGateway;
import com.nithin.ridebooking.payment.PaymentGateway.IntentResult;
import com.nithin.ridebooking.payment.PaymentGateway.WebhookEvent;
import com.nithin.ridebooking.repository.PaymentRepository;
import com.nithin.ridebooking.repository.RideRepository;
import com.nithin.ridebooking.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RideRepository rideRepository;
    private final UserRepository userRepository;
    private final PaymentGateway gateway;
    private final String currency;

    public PaymentService(PaymentRepository paymentRepository,
                          RideRepository rideRepository,
                          UserRepository userRepository,
                          PaymentGateway gateway,
                          @Value("${stripe.currency}") String currency) {
        this.paymentRepository = paymentRepository;
        this.rideRepository = rideRepository;
        this.userRepository = userRepository;
        this.gateway = gateway;
        this.currency = currency.toLowerCase();
    }

    /** Starts (or resumes) the payment for a completed ride. The amount always comes from the server. */
    @Transactional
    public PaymentResponse createPayment(String email, Long rideId) {
        User caller = findUser(email);
        Ride ride = findRide(rideId);

        if (!ride.getRider().getId().equals(caller.getId())) {
            throw new AccessDeniedException("Only the rider can pay for this ride");
        }
        if (ride.getStatus() != RideStatus.COMPLETED) {
            throw new BadRequestException("Ride must be completed before payment");
        }
        if (ride.getFare() == null) {
            throw new BadRequestException("Ride has no fare to charge");
        }

        long amountMinor = toMinorUnits(ride.getFare());
        Optional<Payment> existing = paymentRepository.findByRideId(rideId);

        if (existing.isPresent()) {
            Payment payment = existing.get();

            if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
                throw new BadRequestException("Ride is already paid");
            }
            if (payment.getStatus() == PaymentStatus.PENDING) {
                return toResponse(payment, true);       // resume the same payment
            }

            // FAILED: allow another attempt with a fresh payment
            IntentResult intent = gateway.createIntent(amountMinor, currency, rideId);
            payment.setStripePaymentIntentId(intent.id());
            payment.setClientSecret(intent.clientSecret());
            payment.setStatus(PaymentStatus.PENDING);
            return toResponse(paymentRepository.save(payment), true);
        }

        IntentResult intent = gateway.createIntent(amountMinor, currency, rideId);
        Payment payment = Payment.builder()
                .ride(ride)
                .amount(ride.getFare())
                .currency(currency)
                .status(PaymentStatus.PENDING)
                .stripePaymentIntentId(intent.id())
                .clientSecret(intent.clientSecret())
                .build();
        return toResponse(paymentRepository.save(payment), true);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(String email, Long rideId) {
        User caller = findUser(email);
        Ride ride = findRide(rideId);

        boolean isRider = ride.getRider().getId().equals(caller.getId());
        if (!isRider && caller.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("You are not allowed to view this payment");
        }

        Payment payment = paymentRepository.findByRideId(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("No payment for ride " + rideId));
        return toResponse(payment, false);
    }

    /** Called by Stripe. Safe to receive the same event twice. */
    @Transactional
    public void handleWebhook(String payload, String signatureHeader) {
        WebhookEvent event = gateway.parseWebhook(payload, signatureHeader);

        Optional<Payment> found = paymentRepository.findByStripePaymentIntentId(event.paymentIntentId());
        if (found.isEmpty()) {
            return;     // not one of ours (e.g. a test event): acknowledge and ignore
        }

        Payment payment = found.get();
        switch (event.type()) {
            case "payment_intent.succeeded" -> {
                if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
                    payment.setStatus(PaymentStatus.SUCCEEDED);
                    payment.setPaidAt(LocalDateTime.now());
                    paymentRepository.save(payment);
                }
            }
            case "payment_intent.payment_failed" -> {
                // A late "failed" event must never undo a successful payment
                if (payment.getStatus() == PaymentStatus.PENDING) {
                    payment.setStatus(PaymentStatus.FAILED);
                    paymentRepository.save(payment);
                }
            }
            default -> { }
        }
    }

    // Stripe wants the smallest currency unit: 151.88 INR -> 15188 paise
    private long toMinorUnits(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    private Ride findRide(Long id) {
        return rideRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ride not found with id " + id));
    }

    private PaymentResponse toResponse(Payment p, boolean includeSecret) {
        return new PaymentResponse(p.getId(), p.getRide().getId(), p.getAmount(), p.getCurrency(),
                p.getStatus(), includeSecret ? p.getClientSecret() : null, p.getPaidAt());
    }
}