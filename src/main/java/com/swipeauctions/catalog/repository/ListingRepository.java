package com.swipeauctions.catalog.repository;

import com.swipeauctions.catalog.entity.Listing;
import com.swipeauctions.catalog.enums.ListingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ListingRepository extends JpaRepository<Listing, UUID> {

    List<Listing> findByStatus(ListingStatus status);

    List<Listing> findByCategory_Id(UUID categoryId);

    List<Listing> findBySeller_Id(UUID sellerId);
}
