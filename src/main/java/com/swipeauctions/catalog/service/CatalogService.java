package com.swipeauctions.catalog.service;

import com.swipeauctions.catalog.entity.Category;
import com.swipeauctions.catalog.entity.Listing;
import com.swipeauctions.catalog.enums.ItemCondition;
import com.swipeauctions.catalog.enums.ListingStatus;
import com.swipeauctions.catalog.repository.CategoryRepository;
import com.swipeauctions.catalog.repository.ListingRepository;
import com.swipeauctions.common.exception.BadRequestException;
import com.swipeauctions.common.exception.ResourceNotFoundException;
import com.swipeauctions.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Category + listing management for the general (any-item) catalog. */
@Service
@RequiredArgsConstructor
public class CatalogService {

    private final CategoryRepository categoryRepository;
    private final ListingRepository listingRepository;

    @Transactional
    public Category createCategory(String name, String slug, UUID parentId) {
        if (categoryRepository.existsBySlug(slug)) {
            throw new BadRequestException("Category slug already exists: " + slug);
        }
        Category parent = parentId != null
                ? categoryRepository.findById(parentId)
                        .orElseThrow(() -> new ResourceNotFoundException("Parent category not found"))
                : null;
        return categoryRepository.save(Category.builder().name(name).slug(slug).parent(parent).build());
    }

    @Transactional(readOnly = true)
    public List<Category> listCategories() {
        return categoryRepository.findAll();
    }

    @Transactional
    public Listing createListing(User seller, UUID categoryId, String title, String description, String brand,
                                 ItemCondition condition, String city, String state, String zip,
                                 BigDecimal reservePrice) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        return listingRepository.save(Listing.builder()
                .seller(seller)
                .category(category)
                .title(title)
                .description(description)
                .brand(brand)
                .condition(condition != null ? condition : ItemCondition.USED)
                .city(city)
                .state(state)
                .zip(zip)
                .reservePrice(reservePrice)
                .status(ListingStatus.DRAFT)
                .build());
    }

    @Transactional(readOnly = true)
    public List<Listing> listListings(ListingStatus status) {
        return status != null ? listingRepository.findByStatus(status) : listingRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Listing getListing(UUID id) {
        return listingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));
    }
}
