package com.nithin.ridebooking.repository;

import com.nithin.ridebooking.entity.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DriverRepository extends JpaRepository<Driver, Long> {

    Optional<Driver> findByUserId(Long userId);

    Optional<Driver> findByUserEmail(String email);

    List<Driver> findByAvailableTrue();

    boolean existsByVehicleNumber(String vehicleNumber);

    boolean existsByLicenseNumber(String licenseNumber);

    // Atomic "claim": returns 1 if this call flipped available true -> false, else 0
    @Modifying
    @Query("update Driver d set d.available = false where d.id = :id and d.available = true")
    int claimIfAvailable(@Param("id") Long id);
}