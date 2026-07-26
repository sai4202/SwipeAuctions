package com.swipeauctions.catalog.repository;

import com.swipeauctions.catalog.entity.ListingImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ListingImageRepository extends JpaRepository<ListingImage, UUID> {

    List<ListingImage> findByListing_IdOrderBySortOrderAsc(UUID listingId);

    /** Batch variant for the browse list — one round trip for every listing instead of one per listing. */
    List<ListingImage> findByListing_IdInOrderBySortOrderAsc(List<UUID> listingIds);
}
