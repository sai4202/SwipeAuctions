package com.swipeauctions.catalog.repository;

import com.swipeauctions.catalog.entity.ListingAttribute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ListingAttributeRepository extends JpaRepository<ListingAttribute, UUID> {

    List<ListingAttribute> findByListing_Id(UUID listingId);
}
