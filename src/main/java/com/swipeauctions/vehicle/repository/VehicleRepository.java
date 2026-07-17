package com.swipeauctions.vehicle.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.swipeauctions.vehicle.entity.Vehicle;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    Optional<Vehicle> findByVehicleReferenceNumber(String vehicleReferenceNumber);

    Optional<Vehicle> findByRegistrationNumber(String registrationNumber);

    Optional<Vehicle> findByChassisNumber(String chassisNumber);

    Optional<Vehicle> findByEngineNumber(String engineNumber);

    boolean existsByVehicleReferenceNumber(String vehicleReferenceNumber);

    boolean existsByChassisNumber(String chassisNumber);

    boolean existsByEngineNumber(String engineNumber);

}