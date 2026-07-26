package com.swipeauctions.catalog.repository;

import com.swipeauctions.catalog.entity.ListingAttribute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ListingAttributeRepository extends JpaRepository<ListingAttribute, UUID> {

    List<ListingAttribute> findByListing_Id(UUID listingId);

    /** Batch variant for the browse list — one round trip for every listing instead of one per listing. */
    List<ListingAttribute> findByListing_IdIn(List<UUID> listingIds);

    /** Distinct real values seen for {@code key} within a category — backs the live browse-page filter dropdowns. */
    @Query("select distinct la.value from ListingAttribute la "
            + "where la.listing.category.id = :categoryId and la.key = :key order by la.value")
    List<String> findDistinctValues(@Param("categoryId") UUID categoryId, @Param("key") String key);
}
