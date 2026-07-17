package com.swipeauctions.catalog.repository;

import com.swipeauctions.catalog.entity.ListingImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ListingImageRepository extends JpaRepository<ListingImage, UUID> {

    List<ListingImage> findByListing_IdOrderBySortOrderAsc(UUID listingId);
}
