package com.swipeauctions.vehicle.entity;

import jakarta.persistence.*;
import lombok.*;
import com.swipeauctions.auctions.entity.AuctionItem;
import com.swipeauctions.common.entity.BaseEntity;
import com.swipeauctions.auctions.enums.AssetCategory;
import com.swipeauctions.vehicle.enums.FuelType;
import com.swipeauctions.vehicle.enums.TransmissionType;
import com.swipeauctions.auctions.enums.VehicleCategory;
import com.swipeauctions.vehicle.enums.VehicleType;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "vehicles",
        indexes = {
                @Index(name = "idx_vehicle_reference", columnList = "vehicle_reference_number"),
                @Index(name = "idx_registration_number", columnList = "registration_number"),
                @Index(name = "idx_chassis_number", columnList = "chassis_number"),
                @Index(name = "idx_engine_number", columnList = "engine_number")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle extends BaseEntity {

    @Column(name = "vehicle_reference_number", nullable = false, unique = true, length = 20)
    private String vehicleReferenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_category", nullable = false)
    private AssetCategory assetCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_category", nullable = false)
    private VehicleCategory vehicleCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false)
    private VehicleType vehicleType;

    @Column(name = "registration_number", length = 20)
    private String registrationNumber;

    @Column(name = "chassis_number", unique = true, length = 100)
    private String chassisNumber;

    @Column(name = "engine_number", unique = true, length = 100)
    private String engineNumber;

    @Column(nullable = false, length = 100)
    private String make;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(length = 100)
    private String variant;

    @Column(name = "manufacturing_year")
    private Integer manufacturingYear;

    @Column(name = "registration_year")
    private Integer registrationYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_type")
    private FuelType fuelType;

    @Enumerated(EnumType.STRING)
    @Column(name = "transmission_type")
    private TransmissionType transmission;

    @Column(name = "body_type", length = 100)
    private String bodyType;

    @Column(length = 50)
    private String color;

    @Column(name = "odometer_reading")
    private Integer odometerReading;

    @Column(name = "ownership_number")
    private Integer ownershipNumber;

    @Builder.Default
    @Column(name = "rc_available", nullable = false)
    private Boolean rcAvailable = false;

    @Builder.Default
    @Column(name = "key_available", nullable = false)
    private Boolean keyAvailable = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean hypothecation = false;

    @Builder.Default
    @Column(name = "available_for_auction", nullable = false)
    private Boolean availableForAuction = true;

    @Builder.Default
    @Column(nullable = false)
    private Boolean sold = false;

    @Builder.Default
    @Column(name = "auction_count", nullable = false)
    private Integer auctionCount = 0;

    /*
     * Relationships
     */
    @OneToOne(
            mappedBy = "vehicle",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private VehicleDetails vehicleDetails;

    @Builder.Default
    @OneToMany(
            mappedBy = "vehicle",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY,
            orphanRemoval = true
    )
    private List<VehicleImage> vehicleImages = new ArrayList<>();

    @Builder.Default
    @OneToMany(
            mappedBy = "vehicle",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY,
            orphanRemoval = true
    )
    private List<VehicleDocument> vehicleDocuments = new ArrayList<>();

    @Builder.Default
    @OneToMany(
            mappedBy = "vehicle",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<AuctionItem> auctionItems = new ArrayList<>();
}