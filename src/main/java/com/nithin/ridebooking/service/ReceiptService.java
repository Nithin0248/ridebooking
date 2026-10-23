package com.nithin.ridebooking.service;

import com.nithin.ridebooking.dto.ReceiptResponse;
import com.nithin.ridebooking.entity.*;
import com.nithin.ridebooking.exception.BadRequestException;
import com.nithin.ridebooking.exception.ResourceNotFoundException;
import com.nithin.ridebooking.repository.PaymentRepository;
import com.nithin.ridebooking.repository.RideRepository;
import com.nithin.ridebooking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final RideRepository rideRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public ReceiptResponse getReceipt(String email, Long rideId) {
        User caller = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Ride not found with id " + rideId));

        boolean isRider = ride.getRider().getId().equals(caller.getId());
        boolean isDriver = ride.getDriver() != null
                && ride.getDriver().getUser().getId().equals(caller.getId());
        if (!isRider && !isDriver && caller.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("You are not allowed to view this receipt");
        }
        if (ride.getStatus() != RideStatus.COMPLETED) {
            throw new BadRequestException("Receipt is only available for completed rides");
        }

        Optional<Payment> payment = paymentRepository.findByRideId(rideId);
        Driver driver = ride.getDriver();

        return new ReceiptResponse(
                String.format("RCPT-%06d", ride.getId()),
                ride.getId(),
                ride.getRequestedAt(),
                ride.getCompletedAt(),
                ride.getRider().getName(),
                driver != null ? driver.getUser().getName() : null,
                driver != null ? driver.getVehicleModel() : null,
                driver != null ? driver.getVehicleNumber() : null,
                ride.getPickupAddress(),
                ride.getDropAddress(),
                ride.getDistanceKm(),
                ride.getFare(),
                payment.map(Payment::getCurrency).orElse("inr"),
                payment.map(p -> p.getStatus().name()).orElse("NOT_PAID"));
    }

    public byte[] renderPdf(ReceiptResponse receipt) {
        return ReceiptPdfRenderer.render(receipt);
    }
}