package com.nithin.ridebooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "rides")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rider_id", nullable = false)
    private User rider;

    // Empty until a driver is matched
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    private String pickupAddress;
    private Double pickupLatitude;
    private Double pickupLongitude;

    private String dropAddress;
    private Double dropLatitude;
    private Double dropLongitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RideStatus status;

    private Double distanceKm;

    @Column(precision = 10, scale = 2)
    private BigDecimal fare;

    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;

    @PrePersist
    void onCreate() {
        this.requestedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = RideStatus.REQUESTED;
        }
    }

}