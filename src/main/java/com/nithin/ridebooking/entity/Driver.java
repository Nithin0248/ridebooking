package com.nithin.ridebooking.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "drivers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private String vehicleModel;

    @Column(nullable = false, unique = true)
    private String vehicleNumber;

    @Column(nullable = false, unique = true)
    private String licenseNumber;

    private boolean available;

    // Current location (used for matching in Step 8)
    private Double currentLatitude;
    private Double currentLongitude;
}