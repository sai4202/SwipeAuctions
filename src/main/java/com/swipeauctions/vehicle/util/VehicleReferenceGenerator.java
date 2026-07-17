package com.swipeauctions.vehicle.util;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class VehicleReferenceGenerator {
    public String generateVehicleReferenceGenerator() {

        return "SWAVEH" +
                UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 8)
                        .toUpperCase();
    }
}
