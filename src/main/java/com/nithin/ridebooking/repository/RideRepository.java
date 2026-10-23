package com.nithin.ridebooking.repository;

import com.nithin.ridebooking.entity.Ride;
import com.nithin.ridebooking.entity.RideStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RideRepository extends JpaRepository<Ride, Long> {

    List<Ride> findByRiderIdOrderByRequestedAtDesc(Long riderId);

    List<Ride> findByDriverId(Long driverId);

    List<Ride> findByStatus(RideStatus status);

    boolean existsByRiderIdAndStatusIn(Long riderId, Collection<RideStatus> statuses);

    boolean existsByDriverIdAndStatusIn(Long driverId, Collection<RideStatus> statuses);

    // ---- history (paginated). The entity graph loads rider, driver and driver's user in one query ----

    @EntityGraph(attributePaths = {"rider", "driver", "driver.user"})
    Page<Ride> findByRiderId(Long riderId, Pageable pageable);

    @EntityGraph(attributePaths = {"rider", "driver", "driver.user"})
    Page<Ride> findByRiderIdAndStatus(Long riderId, RideStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"rider", "driver", "driver.user"})
    Page<Ride> findByDriverUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"rider", "driver", "driver.user"})
    Page<Ride> findByDriverUserIdAndStatus(Long userId, RideStatus status, Pageable pageable);
}