package com.swipeauctions.catalog.repository;

import com.swipeauctions.catalog.entity.Listing;
import com.swipeauctions.catalog.enums.ListingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ListingRepository extends JpaRepository<Listing, UUID> {

    List<Listing> findByStatus(ListingStatus status);

    Page<Listing> findByStatus(ListingStatus status, Pageable pageable);

    List<Listing> findByCategory_Id(UUID categoryId);

    List<Listing> findBySeller_Id(UUID sellerId);

    /**
     * Batch-loads listings with their (lazy) category and seller already initialized — used by the
     * auction browse list so rendering N auctions doesn't trigger 2N extra lazy-load round trips
     * (one for category, one for seller, per listing).
     */
    @Query("select l from Listing l join fetch l.category join fetch l.seller where l.id in :ids")
    List<Listing> findAllWithCategoryAndSellerByIdIn(@Param("ids") List<UUID> ids);
}
