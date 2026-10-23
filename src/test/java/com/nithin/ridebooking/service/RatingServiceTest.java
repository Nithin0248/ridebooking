package com.nithin.ridebooking.service;

import com.nithin.ridebooking.dto.RatingRequest;
import com.nithin.ridebooking.dto.RatingResponse;
import com.nithin.ridebooking.dto.RatingSummary;
import com.nithin.ridebooking.entity.*;
import com.nithin.ridebooking.exception.BadRequestException;
import com.nithin.ridebooking.exception.ResourceNotFoundException;
import com.nithin.ridebooking.repository.DriverRepository;
import com.nithin.ridebooking.repository.RatingRepository;
import com.nithin.ridebooking.repository.RideRepository;
import com.nithin.ridebooking.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    @Mock private RatingRepository ratingRepository;
    @Mock private RideRepository rideRepository;
    @Mock private UserRepository userRepository;
    @Mock private DriverRepository driverRepository;

    private RatingService ratingService;

    private final User rider = User.builder().id(1L).name("Rider One").email("r@test.com").role(Role.RIDER).build();
    private final User driverUser = User.builder().id(2L).name("Driver One").email("d@test.com").role(Role.DRIVER).build();
    private final Driver driver = Driver.builder().id(10L).user(driverUser).build();

    @BeforeEach
    void setUp() {
        ratingService = new RatingService(ratingRepository, rideRepository, userRepository, driverRepository);
    }

    private Ride ride(RideStatus status) {
        return Ride.builder().id(5L).rider(rider).driver(driver).status(status).build();
    }

    @Test
    void riderRatesTheDriver() {
        when(userRepository.findByEmail("r@test.com")).thenReturn(Optional.of(rider));
        when(rideRepository.findById(5L)).thenReturn(Optional.of(ride(RideStatus.COMPLETED)));
        when(ratingRepository.existsByRideIdAndRaterId(5L, 1L)).thenReturn(false);
        when(ratingRepository.save(any(Rating.class))).thenAnswer(inv -> inv.getArgument(0));

        RatingResponse response = ratingService.rateRide("r@test.com", 5L, new RatingRequest(4, "  Quick and polite  "));

        assertThat(response.raterId()).isEqualTo(1L);
        assertThat(response.rateeId()).isEqualTo(2L);
        assertThat(response.stars()).isEqualTo(4);
        assertThat(response.comment()).isEqualTo("Quick and polite");
    }

    @Test
    void driverRatesTheRider() {
        when(userRepository.findByEmail("d@test.com")).thenReturn(Optional.of(driverUser));
        when(rideRepository.findById(5L)).thenReturn(Optional.of(ride(RideStatus.COMPLETED)));
        when(ratingRepository.existsByRideIdAndRaterId(5L, 2L)).thenReturn(false);
        when(ratingRepository.save(any(Rating.class))).thenAnswer(inv -> inv.getArgument(0));

        RatingResponse response = ratingService.rateRide("d@test.com", 5L, new RatingRequest(5, "   "));

        assertThat(response.rateeId()).isEqualTo(1L);
        assertThat(response.comment()).isNull();   // blank comments are not stored
    }

    @Test
    void rideThatIsNotCompletedCannotBeRated() {
        when(userRepository.findByEmail("r@test.com")).thenReturn(Optional.of(rider));
        when(rideRepository.findById(5L)).thenReturn(Optional.of(ride(RideStatus.IN_PROGRESS)));

        assertThatThrownBy(() -> ratingService.rateRide("r@test.com", 5L, new RatingRequest(5, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("completed");
        verify(ratingRepository, never()).save(any(Rating.class));
    }

    @Test
    void strangerCannotRateTheRide() {
        User stranger = User.builder().id(9L).email("x@test.com").role(Role.RIDER).build();
        when(userRepository.findByEmail("x@test.com")).thenReturn(Optional.of(stranger));
        when(rideRepository.findById(5L)).thenReturn(Optional.of(ride(RideStatus.COMPLETED)));

        assertThatThrownBy(() -> ratingService.rateRide("x@test.com", 5L, new RatingRequest(1, "spam")))
                .isInstanceOf(AccessDeniedException.class);
        verify(ratingRepository, never()).save(any(Rating.class));
    }

    @Test
    void ratingTheSameRideTwiceIsRejected() {
        when(userRepository.findByEmail("r@test.com")).thenReturn(Optional.of(rider));
        when(rideRepository.findById(5L)).thenReturn(Optional.of(ride(RideStatus.COMPLETED)));
        when(ratingRepository.existsByRideIdAndRaterId(5L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> ratingService.rateRide("r@test.com", 5L, new RatingRequest(5, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already rated");
        verify(ratingRepository, never()).save(any(Rating.class));
    }

    @Test
    void duplicateCaughtByTheDatabaseIsReportedAsBadRequest() {
        when(userRepository.findByEmail("r@test.com")).thenReturn(Optional.of(rider));
        when(rideRepository.findById(5L)).thenReturn(Optional.of(ride(RideStatus.COMPLETED)));
        when(ratingRepository.existsByRideIdAndRaterId(5L, 1L)).thenReturn(false);
        when(ratingRepository.save(any(Rating.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> ratingService.rateRide("r@test.com", 5L, new RatingRequest(5, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already rated");
    }

    @Test
    void summaryRoundsTheAverageToTwoDecimals() {
        when(driverRepository.findById(10L)).thenReturn(Optional.of(driver));
        when(ratingRepository.countByRateeId(2L)).thenReturn(3L);
        when(ratingRepository.averageStars(2L)).thenReturn(4.3333333);

        RatingSummary summary = ratingService.getDriverSummary(10L);

        assertThat(summary.averageStars()).isEqualTo(4.33);
        assertThat(summary.ratingCount()).isEqualTo(3L);
        assertThat(summary.lowRated()).isFalse();
    }

    @Test
    void driverWithNoRatingsHasNoAverage() {
        when(driverRepository.findById(10L)).thenReturn(Optional.of(driver));
        when(ratingRepository.countByRateeId(2L)).thenReturn(0L);
        when(ratingRepository.averageStars(2L)).thenReturn(null);

        RatingSummary summary = ratingService.getDriverSummary(10L);

        assertThat(summary.averageStars()).isNull();
        assertThat(summary.ratingCount()).isZero();
        assertThat(summary.lowRated()).isFalse();
    }

    @Test
    void lowAverageWithEnoughRatingsIsFlagged() {
        when(driverRepository.findById(10L)).thenReturn(Optional.of(driver));
        when(ratingRepository.countByRateeId(2L)).thenReturn(5L);
        when(ratingRepository.averageStars(2L)).thenReturn(3.0);

        assertThat(ratingService.getDriverSummary(10L).lowRated()).isTrue();
    }

    @Test
    void lowAverageWithTooFewRatingsIsNotFlagged() {
        when(driverRepository.findById(10L)).thenReturn(Optional.of(driver));
        when(ratingRepository.countByRateeId(2L)).thenReturn(4L);
        when(ratingRepository.averageStars(2L)).thenReturn(2.0);

        assertThat(ratingService.getDriverSummary(10L).lowRated()).isFalse();
    }

    @Test
    void unknownDriverIsNotFound() {
        when(driverRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ratingService.getDriverSummary(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}