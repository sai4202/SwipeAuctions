package com.swipeauctions.vehicle.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.swipeauctions.vehicle.entity.VehicleImage;

import java.util.UUID;

@Repository
public interface VehicleImageRepository extends JpaRepository<VehicleImage, UUID> {

}