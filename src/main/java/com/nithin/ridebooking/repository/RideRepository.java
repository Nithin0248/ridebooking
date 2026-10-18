package com.nithin.ridebooking.repository;

import com.nithin.ridebooking.entity.Ride;
import com.nithin.ridebooking.entity.RideStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RideRepository extends JpaRepository<Ride, Long> {

    List<Ride> findByRiderIdOrderByRequestedAtDesc(Long riderId);

    List<Ride> findByDriverId(Long driverId);

    List<Ride> findByStatus(RideStatus status);

    boolean existsByRiderIdAndStatusIn(Long riderId, Collection<RideStatus> statuses);

    boolean existsByDriverIdAndStatusIn(Long driverId, Collection<RideStatus> statuses);
}