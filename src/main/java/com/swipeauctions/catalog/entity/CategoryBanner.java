package com.swipeauctions.catalog.entity;

import com.swipeauctions.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/** A promotional image slide, optionally scoped to one category (null = platform-wide) — shown on
 *  the homepage/category pages, ordered by {@code sortOrder}. Purely additive/decorative content;
 *  doesn't affect catalog, bidding, or wallet logic. */
@Entity
@Table(name = "category_banners", indexes = {
        @Index(name = "idx_category_banners_category", columnList = "category_id"),
        @Index(name = "idx_category_banners_active", columnList = "active"),
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryBanner extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "link_url", length = 500)
    private String linkUrl;

    @Column(length = 200)
    private String title;

    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
