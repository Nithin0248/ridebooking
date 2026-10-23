package com.nithin.ridebooking.repository;

import com.nithin.ridebooking.entity.Rating;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    boolean existsByRideIdAndRaterId(Long rideId, Long raterId);

    List<Rating> findByRideId(Long rideId);

    long countByRateeId(Long rateeId);

    // null when the user has no ratings yet
    @Query("select avg(r.stars) from Rating r where r.ratee.id = :userId")
    Double averageStars(@Param("userId") Long userId);

    // the entity graph loads the rater in the same query, so listing reviews does not run one query per row
    @EntityGraph(attributePaths = {"rater"})
    Page<Rating> findByRateeId(Long rateeId, Pageable pageable);
}