package com.swipeauctions.catalog.entity;

import com.swipeauctions.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/** Flexible per-listing spec (e.g. mileage=32000, screen_size=15.6"), governed by the category's defs. */
@Entity
@Table(
        name = "listing_attributes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"listing_id", "attr_key"}),
        indexes = @Index(name = "idx_listing_attr_key_value", columnList = "attr_key, attr_value")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListingAttribute extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @Column(name = "attr_key", nullable = false, length = 100)
    private String key;

    @Column(name = "attr_value", nullable = false, columnDefinition = "TEXT")
    private String value;
}
