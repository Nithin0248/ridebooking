package com.nithin.ridebooking.service;

import com.nithin.ridebooking.dto.ReceiptResponse;
import com.nithin.ridebooking.entity.*;
import com.nithin.ridebooking.exception.BadRequestException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptServiceTest {

    @Mock private RideRepository rideRepository;
    @Mock private UserRepository userRepository;
    @Mock private PaymentRepository paymentRepository;

    private ReceiptService receiptService;

    @BeforeEach
    void setUp() {
        receiptService = new ReceiptService(rideRepository, userRepository, paymentRepository);
    }

    private User rider() {
        return User.builder().id(1L).name("Rider One").email("r@test.com").role(Role.RIDER).build();
    }

    private Ride ride(RideStatus status) {
        User driverUser = User.builder().id(2L).name("Driver One").email("d@test.com").role(Role.DRIVER).build();
        Driver driver = Driver.builder().id(10L).user(driverUser)
                .vehicleModel("Swift").vehicleNumber("KA01AB1111").build();
        return Ride.builder().id(5L).rider(rider()).driver(driver).status(status)
                .pickupAddress("Station Road").dropAddress("City Mall")
                .distanceKm(6.74).fare(new BigDecimal("151.88")).build();
    }

    @Test
    void riderGetsAReceiptWithPaymentStatus() {
        Payment payment = Payment.builder().currency("inr").status(PaymentStatus.SUCCEEDED).build();
        when(userRepository.findByEmail("r@test.com")).thenReturn(Optional.of(rider()));
        when(rideRepository.findById(5L)).thenReturn(Optional.of(ride(RideStatus.COMPLETED)));
        when(paymentRepository.findByRideId(5L)).thenReturn(Optional.of(payment));

        ReceiptResponse receipt = receiptService.getReceipt("r@test.com", 5L);

        assertThat(receipt.receiptNumber()).isEqualTo("RCPT-000005");
        assertThat(receipt.driverName()).isEqualTo("Driver One");
        assertThat(receipt.paymentStatus()).isEqualTo("SUCCEEDED");
        assertThat(receipt.fare()).isEqualByComparingTo("151.88");
    }

    @Test
    void unpaidRideShowsNotPaid() {
        when(userRepository.findByEmail("r@test.com")).thenReturn(Optional.of(rider()));
        when(rideRepository.findById(5L)).thenReturn(Optional.of(ride(RideStatus.COMPLETED)));
        when(paymentRepository.findByRideId(5L)).thenReturn(Optional.empty());

        assertThat(receiptService.getReceipt("r@test.com", 5L).paymentStatus()).isEqualTo("NOT_PAID");
    }

    @Test
    void assignedDriverCanAlsoSeeTheReceipt() {
        User driverUser = User.builder().id(2L).email("d@test.com").role(Role.DRIVER).build();
        when(userRepository.findByEmail("d@test.com")).thenReturn(Optional.of(driverUser));
        when(rideRepository.findById(5L)).thenReturn(Optional.of(ride(RideStatus.COMPLETED)));
        when(paymentRepository.findByRideId(5L)).thenReturn(Optional.empty());

        assertThat(receiptService.getReceipt("d@test.com", 5L).rideId()).isEqualTo(5L);
    }

    @Test
    void strangerCannotSeeTheReceipt() {
        User stranger = User.builder().id(9L).email("x@test.com").role(Role.RIDER).build();
        when(userRepository.findByEmail("x@test.com")).thenReturn(Optional.of(stranger));
        when(rideRepository.findById(5L)).thenReturn(Optional.of(ride(RideStatus.COMPLETED)));

        assertThatThrownBy(() -> receiptService.getReceipt("x@test.com", 5L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void receiptIsOnlyForCompletedRides() {
        when(userRepository.findByEmail("r@test.com")).thenReturn(Optional.of(rider()));
        when(rideRepository.findById(5L)).thenReturn(Optional.of(ride(RideStatus.IN_PROGRESS)));

        assertThatThrownBy(() -> receiptService.getReceipt("r@test.com", 5L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("completed");
    }
}