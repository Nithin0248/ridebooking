package com.nithin.ridebooking.service;

import com.nithin.ridebooking.dto.FareEstimate;
import com.nithin.ridebooking.dto.RideRequest;
import com.nithin.ridebooking.dto.RideResponse;
import com.nithin.ridebooking.dto.RideStatusMessage;
import com.nithin.ridebooking.entity.*;
import com.nithin.ridebooking.exception.BadRequestException;
import com.nithin.ridebooking.repository.RideRepository;
import com.nithin.ridebooking.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RideServiceTest {

    @Mock private RideRepository rideRepository;
    @Mock private UserRepository userRepository;
    @Mock private FareService fareService;
    @Mock private MatchingService matchingService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private RideService rideService;

    private User user(Long id, String email, Role role) {
        return User.builder().id(id).name("U" + id).email(email).role(role).build();
    }

    private Driver driverFor(User driverUser) {
        return Driver.builder().id(10L).user(driverUser).vehicleModel("Swift")
                .vehicleNumber("KA01AB1111").available(false).build();
    }

    private RideRequest request() {
        return new RideRequest("Station Road", 12.9716, 77.5946, "City Mall", 12.9352, 77.6245);
    }

    private FareEstimate fare() {
        return new FareEstimate(6.74, 14, new BigDecimal("50.00"), new BigDecimal("80.88"),
                new BigDecimal("21.00"), BigDecimal.ONE, new BigDecimal("151.88"));
    }

    @Test
    void bookRideAssignsTheMatchedDriver() {
        User rider = user(1L, "r@test.com", Role.RIDER);
        Driver driver = driverFor(user(2L, "d@test.com", Role.DRIVER));
        when(userRepository.findByEmail("r@test.com")).thenReturn(Optional.of(rider));
        when(rideRepository.existsByRiderIdAndStatusIn(eq(1L), anyCollection())).thenReturn(false);
        when(fareService.estimate(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(fare());
        when(matchingService.findAndClaimNearestDriver(anyDouble(), anyDouble()))
                .thenReturn(Optional.of(driver));
        when(rideRepository.save(any(Ride.class))).thenAnswer(inv -> inv.getArgument(0));

        RideResponse response = rideService.bookRide("r@test.com", request());

        assertThat(response.status()).isEqualTo(RideStatus.ACCEPTED);
        assertThat(response.driverName()).isEqualTo("U2");
        assertThat(response.fare()).isEqualByComparingTo("151.88");
        verify(eventPublisher).publishEvent(any(RideStatusMessage.class));
    }

    @Test
    void bookRideStaysRequestedWhenNoDriverIsNearby() {
        User rider = user(1L, "r@test.com", Role.RIDER);
        when(userRepository.findByEmail("r@test.com")).thenReturn(Optional.of(rider));
        when(rideRepository.existsByRiderIdAndStatusIn(eq(1L), anyCollection())).thenReturn(false);
        when(fareService.estimate(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(fare());
        when(matchingService.findAndClaimNearestDriver(anyDouble(), anyDouble()))
                .thenReturn(Optional.empty());
        when(rideRepository.save(any(Ride.class))).thenAnswer(inv -> inv.getArgument(0));

        RideResponse response = rideService.bookRide("r@test.com", request());

        assertThat(response.status()).isEqualTo(RideStatus.REQUESTED);
        assertThat(response.driverId()).isNull();
    }

    @Test
    void onlyRidersCanBookRides() {
        User driverUser = user(2L, "d@test.com", Role.DRIVER);
        when(userRepository.findByEmail("d@test.com")).thenReturn(Optional.of(driverUser));

        assertThatThrownBy(() -> rideService.bookRide("d@test.com", request()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only riders");
        verify(rideRepository, never()).save(any(Ride.class));
    }

    @Test
    void riderWithAnActiveRideCannotBookAnother() {
        User rider = user(1L, "r@test.com", Role.RIDER);
        when(userRepository.findByEmail("r@test.com")).thenReturn(Optional.of(rider));
        when(rideRepository.existsByRiderIdAndStatusIn(eq(1L), anyCollection())).thenReturn(true);

        assertThatThrownBy(() -> rideService.bookRide("r@test.com", request()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("active ride");
        verifyNoInteractions(matchingService);
    }

    @Test
    void cancelByAnotherUserIsForbidden() {
        User owner = user(1L, "owner@test.com", Role.RIDER);
        User other = user(3L, "other@test.com", Role.RIDER);
        Ride ride = Ride.builder().id(5L).rider(owner).status(RideStatus.REQUESTED).build();
        when(userRepository.findByEmail("other@test.com")).thenReturn(Optional.of(other));
        when(rideRepository.findById(5L)).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.cancelRide("other@test.com", 5L))
                .isInstanceOf(AccessDeniedException.class);
        verify(rideRepository, never()).save(any(Ride.class));
    }

    @Test
    void cancellingFreesTheDriver() {
        User rider = user(1L, "r@test.com", Role.RIDER);
        Driver driver = driverFor(user(2L, "d@test.com", Role.DRIVER));
        Ride ride = Ride.builder().id(5L).rider(rider).driver(driver)
                .status(RideStatus.ACCEPTED).build();
        when(userRepository.findByEmail("r@test.com")).thenReturn(Optional.of(rider));
        when(rideRepository.findById(5L)).thenReturn(Optional.of(ride));
        when(rideRepository.save(any(Ride.class))).thenAnswer(inv -> inv.getArgument(0));

        RideResponse response = rideService.cancelRide("r@test.com", 5L);

        assertThat(response.status()).isEqualTo(RideStatus.CANCELLED);
        assertThat(driver.isAvailable()).isTrue();
        verify(eventPublisher).publishEvent(any(RideStatusMessage.class));
    }

    @Test
    void completedRideCannotBeCancelled() {
        User rider = user(1L, "r@test.com", Role.RIDER);
        Ride ride = Ride.builder().id(5L).rider(rider).status(RideStatus.COMPLETED).build();
        when(userRepository.findByEmail("r@test.com")).thenReturn(Optional.of(rider));
        when(rideRepository.findById(5L)).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.cancelRide("r@test.com", 5L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot be cancelled");
    }

    @Test
    void onlyTheAssignedDriverCanStartARide() {
        User rider = user(1L, "r@test.com", Role.RIDER);
        Driver driver = driverFor(user(2L, "d@test.com", Role.DRIVER));
        User stranger = user(9L, "x@test.com", Role.DRIVER);
        Ride ride = Ride.builder().id(5L).rider(rider).driver(driver)
                .status(RideStatus.ACCEPTED).build();
        when(userRepository.findByEmail("x@test.com")).thenReturn(Optional.of(stranger));
        when(rideRepository.findById(5L)).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.startRide("x@test.com", 5L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void completingFreesTheDriverAtTheDropOffPoint() {
        User rider = user(1L, "r@test.com", Role.RIDER);
        User driverUser = user(2L, "d@test.com", Role.DRIVER);
        Driver driver = driverFor(driverUser);
        Ride ride = Ride.builder().id(5L).rider(rider).driver(driver)
                .dropLatitude(12.9352).dropLongitude(77.6245)
                .status(RideStatus.IN_PROGRESS).build();
        when(userRepository.findByEmail("d@test.com")).thenReturn(Optional.of(driverUser));
        when(rideRepository.findById(5L)).thenReturn(Optional.of(ride));
        when(rideRepository.save(any(Ride.class))).thenAnswer(inv -> inv.getArgument(0));

        RideResponse response = rideService.completeRide("d@test.com", 5L);

        assertThat(response.status()).isEqualTo(RideStatus.COMPLETED);
        assertThat(response.completedAt()).isNotNull();
        assertThat(driver.isAvailable()).isTrue();
        assertThat(driver.getCurrentLatitude()).isEqualTo(12.9352);
        assertThat(driver.getCurrentLongitude()).isEqualTo(77.6245);
    }
}