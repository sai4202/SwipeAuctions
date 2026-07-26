package com.swipeauctions.catalog.repository;

import com.swipeauctions.catalog.entity.CategoryAttributeDef;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CategoryAttributeDefRepository extends JpaRepository<CategoryAttributeDef, UUID> {

    List<CategoryAttributeDef> findByCategory_IdOrderBySortOrderAsc(UUID categoryId);

    List<CategoryAttributeDef> findByCategory_IdAndFilterableTrue(UUID categoryId);

    List<CategoryAttributeDef> findByCategory_IdAndFilterableTrueOrderBySortOrderAsc(UUID categoryId);
}
