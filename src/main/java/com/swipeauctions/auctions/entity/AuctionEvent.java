package com.swipeauctions.auctions.entity;

import jakarta.persistence.*;
import lombok.*;
import com.swipeauctions.auctions.enums.AssetCategory;
import com.swipeauctions.auctions.enums.AuctionMode;
import com.swipeauctions.auctions.enums.AuctionStatus;
import com.swipeauctions.auctions.enums.ProviderType;
import com.swipeauctions.auctions.enums.VehicleCategory;
import com.swipeauctions.common.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(
        name = "auction_events",
        indexes = {
                @Index(name = "idx_event_code", columnList = "event_code"),
                @Index(name = "idx_provider", columnList = "provider_type"),
                @Index(name = "idx_asset_category", columnList = "asset_category"),
                @Index(name = "idx_auction_status", columnList = "auction_status"),
                @Index(name = "idx_start_time", columnList = "start_time"),
                @Index(name = "idx_end_time", columnList = "end_time")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuctionEvent extends BaseEntity {

    @Column(
            name = "event_code",
            nullable = false,
            unique = true,
            length = 25
    )
    private String eventCode;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false)
    private ProviderType providerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_category", nullable = false)
    private AssetCategory assetCategory;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "auction_event_vehicle_categories",
            joinColumns = @JoinColumn(name = "auction_event_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(
            name = "vehicle_category",
            nullable = false
    )
    @Builder.Default
    private Set<VehicleCategory> vehicleCategories = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "auction_status", nullable = false)
    private AuctionStatus auctionStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "auction_mode", nullable = false)
    private AuctionMode auctionMode;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(length = 100)
    private String region;

    @Column(nullable = false, length = 100)
    private String state;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(name = "banner_image")
    private String bannerImage;

    @Builder.Default
    @Column(nullable = false)
    private Boolean featured = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean published = false;

    @Builder.Default
    @Column(name = "display_order")
    private Integer displayOrder = 0;

    @Builder.Default
    @Column(name = "total_lots", nullable = false)
    private Integer totalLots = 0;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Column(
            name = "terms_and_conditions",
            columnDefinition = "TEXT"
    )
    private String termsAndConditions;

    @Builder.Default
    @OneToMany(
            mappedBy = "auctionEvent",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<AuctionItem> auctionItems = new ArrayList<>();

}