package com.swipeauctions.vehicle.entity;

import jakarta.persistence.*;
import lombok.*;
import com.swipeauctions.common.entity.BaseEntity;
import com.swipeauctions.vehicle.enums.InspectionCondition;
import com.swipeauctions.vehicle.enums.RunningCondition;
import com.swipeauctions.vehicle.enums.StartCondition;

@Entity
@Table(name = "vehicle_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleDetails extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "vehicle_id",
            nullable = false,
            unique = true
    )
    private Vehicle vehicle;

    @Column(name = "primary_damage", length = 100)
    private String primaryDamage;

    @Column(name = "secondary_damage", length = 100)
    private String secondaryDamage;

    @Builder.Default
    @Column(name = "accident_history", nullable = false)
    private Boolean accidentHistory = false;

    @Builder.Default
    @Column(name = "flood_affected", nullable = false)
    private Boolean floodAffected = false;

    @Builder.Default
    @Column(name = "fire_damaged", nullable = false)
    private Boolean fireDamaged = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "running_condition")
    private RunningCondition runningCondition;

    @Enumerated(EnumType.STRING)
    @Column(name = "start_condition")
    private StartCondition startCondition;

    @Enumerated(EnumType.STRING)
    @Column(name = "tyre_condition")
    private InspectionCondition tyreCondition;

    @Enumerated(EnumType.STRING)
    @Column(name = "battery_condition")
    private InspectionCondition batteryCondition;

    @Enumerated(EnumType.STRING)
    @Column(name = "interior_condition")
    private InspectionCondition interiorCondition;

    @Enumerated(EnumType.STRING)
    @Column(name = "exterior_condition")
    private InspectionCondition exteriorCondition;

    @Column(name = "seller_remarks", columnDefinition = "TEXT")
    private String sellerRemarks;

    @Column(name = "inspection_remarks", columnDefinition = "TEXT")
    private String inspectionRemarks;
}