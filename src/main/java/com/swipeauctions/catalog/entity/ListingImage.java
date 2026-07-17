package com.swipeauctions.catalog.entity;

import com.swipeauctions.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/** An image for a listing, with ordering and a single designated cover. */
@Entity
@Table(name = "listing_images", indexes = @Index(name = "idx_listing_images_listing", columnList = "listing_id, sort_order"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListingImage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @Column(nullable = false, length = 1000)
    private String url;

    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Builder.Default
    @Column(name = "is_cover", nullable = false)
    private boolean cover = false;
}
