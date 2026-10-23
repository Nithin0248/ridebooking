package com.nithin.ridebooking.service;

import com.nithin.ridebooking.dto.PaymentResponse;
import com.nithin.ridebooking.entity.*;
import com.nithin.ridebooking.exception.BadRequestException;
import com.nithin.ridebooking.payment.PaymentGateway;
import com.nithin.ridebooking.payment.PaymentGateway.IntentResult;
import com.nithin.ridebooking.payment.PaymentGateway.WebhookEvent;
import com.nithin.ridebooking.repository.PaymentRepository;
import com.nithin.ridebooking.repository.RideRepository;
import com.nithin.ridebooking.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private RideRepository rideRepository;
    @Mock private UserRepository userRepository;
    @Mock private PaymentGateway gateway;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, rideRepository, userRepository, gateway, "inr");
    }

    private User rider() {
        return User.builder().id(1L).email("r@test.com").name("Rider").role(Role.RIDER).build();
    }

    private Ride ride(RideStatus status) {
        return Ride.builder().id(5L).rider(rider()).status(status)
                .fare(new BigDecimal("151.88")).build();
    }

    private Payment payment(PaymentStatus status) {
        return Payment.builder().id(9L).ride(ride(RideStatus.COMPLETED)).amount(new BigDecimal("151.88"))
                .currency("inr").status(status).stripePaymentIntentId("pi_1").clientSecret("secret_1").build();
    }

    @Test
    void completedRideCreatesAPendingPaymentInPaise() {
        when(userRepository.findByEmail("r@test.com")).thenReturn(Optional.of(rider()));
        when(rideRepository.findById(5L)).thenReturn(Optional.of(ride(RideStatus.COMPLETED)));
        when(paymentRepository.findByRideId(5L)).thenReturn(Optional.empty());
        when(gateway.createIntent(15188L, "inr", 5L)).thenReturn(new IntentResult("pi_1", "secret_1"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = paymentService.createPayment("r@test.com", 5L);

        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.clientSecret()).isEqualTo("secret_1");
        assertThat(response.amount()).isEqualByComparingTo("151.88");
    }

    @Test
    void rideThatIsNotCompletedCannotBePaid() {
        when(userRepository.findByEmail("r@test.com")).thenReturn(Optional.of(rider()));
        when(rideRepository.findById(5L)).thenReturn(Optional.of(ride(RideStatus.ACCEPTED)));

        assertThatThrownBy(() -> paymentService.createPayment("r@test.com", 5L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("completed");
        verifyNoInteractions(gateway);
    }

    @Test
    void anotherUserCannotPayForSomeoneElsesRide() {
        User other = User.builder().id(3L).email("o@test.com").role(Role.RIDER).build();
        when(userRepository.findByEmail("o@test.com")).thenReturn(Optional.of(other));
        when(rideRepository.findById(5L)).thenReturn(Optional.of(ride(RideStatus.COMPLETED)));

        assertThatThrownBy(() -> paymentService.createPayment("o@test.com", 5L))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(gateway);
    }

    @Test
    void alreadyPaidRideCannotBePaidTwice() {
        when(userRepository.findByEmail("r@test.com")).thenReturn(Optional.of(rider()));
        when(rideRepository.findById(5L)).thenReturn(Optional.of(ride(RideStatus.COMPLETED)));
        when(paymentRepository.findByRideId(5L)).thenReturn(Optional.of(payment(PaymentStatus.SUCCEEDED)));

        assertThatThrownBy(() -> paymentService.createPayment("r@test.com", 5L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already paid");
        verifyNoInteractions(gateway);
    }

    @Test
    void pendingPaymentIsResumedWithoutCallingStripeAgain() {
        when(userRepository.findByEmail("r@test.com")).thenReturn(Optional.of(rider()));
        when(rideRepository.findById(5L)).thenReturn(Optional.of(ride(RideStatus.COMPLETED)));
        when(paymentRepository.findByRideId(5L)).thenReturn(Optional.of(payment(PaymentStatus.PENDING)));

        PaymentResponse response = paymentService.createPayment("r@test.com", 5L);

        assertThat(response.clientSecret()).isEqualTo("secret_1");
        verifyNoInteractions(gateway);
    }

    @Test
    void successWebhookMarksThePaymentPaid() {
        Payment pending = payment(PaymentStatus.PENDING);
        when(gateway.parseWebhook("body", "sig")).thenReturn(new WebhookEvent("payment_intent.succeeded", "pi_1"));
        when(paymentRepository.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(pending));

        paymentService.handleWebhook("body", "sig");

        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(pending.getPaidAt()).isNotNull();
    }

    @Test
    void lateFailureWebhookNeverUndoesASuccessfulPayment() {
        Payment paid = payment(PaymentStatus.SUCCEEDED);
        when(gateway.parseWebhook("body", "sig"))
                .thenReturn(new WebhookEvent("payment_intent.payment_failed", "pi_1"));
        when(paymentRepository.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(paid));

        paymentService.handleWebhook("body", "sig");

        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void failureWebhookMarksAPendingPaymentFailed() {
        Payment pending = payment(PaymentStatus.PENDING);
        when(gateway.parseWebhook("body", "sig"))
                .thenReturn(new WebhookEvent("payment_intent.payment_failed", "pi_1"));
        when(paymentRepository.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(pending));

        paymentService.handleWebhook("body", "sig");

        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void webhookForAnUnknownPaymentIsIgnored() {
        when(gateway.parseWebhook("body", "sig")).thenReturn(new WebhookEvent("payment_intent.succeeded", "pi_x"));
        when(paymentRepository.findByStripePaymentIntentId("pi_x")).thenReturn(Optional.empty());

        assertThatCode(() -> paymentService.handleWebhook("body", "sig")).doesNotThrowAnyException();
        verify(paymentRepository, never()).save(any(Payment.class));
    }
}