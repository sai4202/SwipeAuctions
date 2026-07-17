package com.swipeauctions.catalog.entity;

import com.swipeauctions.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Hierarchical, category-agnostic classification (e.g. Vehicles &gt; Cars, Electronics &gt; Laptops).
 * A new category is data, never a schema change.
 */
@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;
}
