package com.swipeauctions.catalog.repository;

import com.swipeauctions.catalog.entity.CategoryBanner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CategoryBannerRepository extends JpaRepository<CategoryBanner, UUID> {

    List<CategoryBanner> findByActiveTrueOrderBySortOrderAsc();

    List<CategoryBanner> findAllByOrderBySortOrderAsc();
}
