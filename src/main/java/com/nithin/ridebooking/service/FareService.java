package com.nithin.ridebooking.service;

import com.nithin.ridebooking.dto.FareEstimate;
import com.nithin.ridebooking.exception.BadRequestException;
import com.nithin.ridebooking.util.GeoUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class FareService {

    private final BigDecimal baseFare;
    private final BigDecimal perKm;
    private final BigDecimal perMinute;
    private final BigDecimal minimumFare;
    private final BigDecimal surgeMultiplier;
    private final double roadFactor;
    private final double avgSpeedKmh;

    public FareService(@Value("${fare.base}") BigDecimal baseFare,
                       @Value("${fare.per-km}") BigDecimal perKm,
                       @Value("${fare.per-minute}") BigDecimal perMinute,
                       @Value("${fare.minimum}") BigDecimal minimumFare,
                       @Value("${fare.surge-multiplier}") BigDecimal surgeMultiplier,
                       @Value("${fare.road-factor}") double roadFactor,
                       @Value("${fare.avg-speed-kmh}") double avgSpeedKmh) {
        this.baseFare = baseFare;
        this.perKm = perKm;
        this.perMinute = perMinute;
        this.minimumFare = minimumFare;
        this.surgeMultiplier = surgeMultiplier;
        this.roadFactor = roadFactor;
        this.avgSpeedKmh = avgSpeedKmh;
    }

    public FareEstimate estimate(double pickupLat, double pickupLng,
                                 double dropLat, double dropLng) {
        validateCoordinates(pickupLat, pickupLng);
        validateCoordinates(dropLat, dropLng);

        double straightKm = GeoUtils.distanceKm(pickupLat, pickupLng, dropLat, dropLng);
        double distanceKm = Math.round(straightKm * roadFactor * 100.0) / 100.0;
        int durationMinutes = (int) Math.ceil(distanceKm / avgSpeedKmh * 60);

        BigDecimal base = baseFare.setScale(2, RoundingMode.HALF_UP);
        BigDecimal distanceCharge = perKm.multiply(BigDecimal.valueOf(distanceKm))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal timeCharge = perMinute.multiply(BigDecimal.valueOf(durationMinutes))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal subtotal = base.add(distanceCharge).add(timeCharge);
        BigDecimal surged = subtotal.multiply(surgeMultiplier).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = surged.max(minimumFare.setScale(2, RoundingMode.HALF_UP));

        return new FareEstimate(distanceKm, durationMinutes, base,
                distanceCharge, timeCharge, surgeMultiplier, total);
    }

    private void validateCoordinates(double lat, double lng) {
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new BadRequestException(
                    "Invalid coordinates: latitude must be -90 to 90, longitude -180 to 180");
        }
    }
}