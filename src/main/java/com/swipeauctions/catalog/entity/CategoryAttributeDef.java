package com.swipeauctions.catalog.entity;

import com.swipeauctions.catalog.enums.AttributeValueType;
import com.swipeauctions.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Declares which specs exist for a category (mileage for Cars, screen size for Laptops) and whether
 * the browse page should expose each as a filter. Adding specs to a category is data, not a migration.
 */
@Entity
@Table(
        name = "category_attribute_defs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"category_id", "attr_key"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryAttributeDef extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "attr_key", nullable = false, length = 100)
    private String key;

    @Column(nullable = false, length = 150)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttributeValueType valueType;

    @Builder.Default
    @Column(nullable = false)
    private boolean filterable = true;

    @Builder.Default
    @Column(nullable = false)
    private int sortOrder = 0;
}
