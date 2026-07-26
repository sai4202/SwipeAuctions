package com.swipeauctions.catalog.entity;

import com.swipeauctions.catalog.enums.ItemCondition;
import com.swipeauctions.catalog.enums.ListingStatus;
import com.swipeauctions.common.entity.BaseEntity;
import com.swipeauctions.enums.SubscriptionTier;
import com.swipeauctions.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * A general, category-agnostic item put up for auction. Category-specific specs live in
 * {@link ListingAttribute}; location fields back distance search. The reserve price feeds both the
 * auction's base price and the wallet-hold amount.
 */
@Entity
@Table(name = "listings", indexes = {
        @Index(name = "idx_listings_category", columnList = "category_id"),
        @Index(name = "idx_listings_seller", columnList = "seller_id"),
        @Index(name = "idx_listings_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Listing extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String brand;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemCondition condition;

    @Column(length = 100)
    private String city;

    @Column(length = 50)
    private String state;

    @Column(length = 20)
    private String zip;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(columnDefinition = "TEXT")
    private String sellerNotes;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal reservePrice;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingStatus status = ListingStatus.DRAFT;

    /** Shows this item on the dedicated /swipe-stock page, independent of who the seller is. */
    @Builder.Default
    @Column(name = "swipe_stock", nullable = false)
    private boolean swipeStock = false;

    /** Minimum subscription tier a viewer needs to register/bid on this listing's auction. NONE
     *  (default) means visible to everyone, same as before this field existed. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "required_tier", nullable = false, length = 20)
    private SubscriptionTier requiredTier = SubscriptionTier.NONE;
}
