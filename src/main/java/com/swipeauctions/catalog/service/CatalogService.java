package com.swipeauctions.catalog.service;

import com.swipeauctions.catalog.entity.Category;
import com.swipeauctions.catalog.entity.CategoryAttributeDef;
import com.swipeauctions.catalog.entity.Listing;
import com.swipeauctions.catalog.entity.ListingAttribute;
import com.swipeauctions.catalog.entity.ListingImage;
import com.swipeauctions.catalog.enums.AttributeValueType;
import com.swipeauctions.catalog.enums.ItemCondition;
import com.swipeauctions.catalog.enums.ListingStatus;
import com.swipeauctions.catalog.repository.CategoryAttributeDefRepository;
import com.swipeauctions.catalog.repository.CategoryRepository;
import com.swipeauctions.catalog.repository.ListingAttributeRepository;
import com.swipeauctions.catalog.repository.ListingImageRepository;
import com.swipeauctions.catalog.repository.ListingRepository;
import com.swipeauctions.common.exception.BadRequestException;
import com.swipeauctions.common.exception.ResourceNotFoundException;
import com.swipeauctions.enums.SubscriptionTier;
import com.swipeauctions.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Category + listing management for the general (any-item) catalog. */
@Service
@RequiredArgsConstructor
public class CatalogService {

    private final CategoryRepository categoryRepository;
    private final CategoryAttributeDefRepository categoryAttributeDefRepository;
    private final ListingRepository listingRepository;
    private final ListingAttributeRepository listingAttributeRepository;
    private final ListingImageRepository listingImageRepository;

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

    /**
     * Looks up a category by name (case-insensitive), creating it on the fly if it doesn't exist yet
     * — used by the admin "Add Stock" flow so a brand-new category typed there becomes real, filterable
     * data immediately (same as any admin-created category), no separate "create category" step needed.
     */
    @Transactional
    public Category resolveOrCreateCategory(String name) {
        String trimmed = name.trim();
        return categoryRepository.findAll().stream()
                .filter(c -> c.getName().equalsIgnoreCase(trimmed))
                .findFirst()
                .orElseGet(() -> {
                    String slug = slugify(trimmed);
                    String uniqueSlug = slug;
                    int suffix = 2;
                    while (categoryRepository.existsBySlug(uniqueSlug)) {
                        uniqueSlug = slug + "-" + suffix++;
                    }
                    return createCategory(trimmed, uniqueSlug, null);
                });
    }

    private static String slugify(String name) {
        String slug = name.toLowerCase().trim().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return slug.isEmpty() ? "category" : slug;
    }

    /** Admin-only: declare a filterable/display spec for a category (e.g. "Fuel" for Vehicles). */
    @Transactional
    public CategoryAttributeDef addCategoryAttribute(UUID categoryId, String key, String label,
                                                      AttributeValueType valueType, boolean filterable, int sortOrder) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        return categoryAttributeDefRepository.save(CategoryAttributeDef.builder()
                .category(category).key(key).label(label).valueType(valueType)
                .filterable(filterable).sortOrder(sortOrder).build());
    }

    @Transactional(readOnly = true)
    public List<CategoryAttributeDef> listCategoryAttributes(UUID categoryId) {
        return categoryAttributeDefRepository.findByCategory_IdOrderBySortOrderAsc(categoryId);
    }

    /**
     * Public browse-page filters for a category: each filterable def, paired with the distinct real
     * values seen for it (empty for NUMBER defs, which the browse page renders as a free numeric input
     * rather than a dropdown). No curated option lists — a category shows exactly what its own listings
     * actually have.
     */
    @Transactional(readOnly = true)
    public List<CategoryFilterOption> listPublicFilters(UUID categoryId) {
        return categoryAttributeDefRepository.findByCategory_IdAndFilterableTrueOrderBySortOrderAsc(categoryId).stream()
                .map(def -> new CategoryFilterOption(def.getKey(), def.getLabel(), def.getValueType(),
                        def.getValueType() == AttributeValueType.NUMBER
                                ? List.of()
                                : listingAttributeRepository.findDistinctValues(categoryId, def.getKey())))
                .toList();
    }

    public record CategoryFilterOption(String key, String label, AttributeValueType valueType, List<String> options) {}

    @Transactional
    public Listing createListing(User seller, UUID categoryId, String title, String description, String brand,
                                 ItemCondition condition, String city, String state, String zip,
                                 BigDecimal reservePrice, Map<String, String> attributes) {
        return createListing(seller, categoryId, title, description, brand, condition, city, state, zip,
                reservePrice, attributes, false);
    }

    @Transactional
    public Listing createListing(User seller, UUID categoryId, String title, String description, String brand,
                                 ItemCondition condition, String city, String state, String zip,
                                 BigDecimal reservePrice, Map<String, String> attributes, boolean swipeStock) {
        return createListing(seller, categoryId, title, description, brand, condition, city, state, zip,
                reservePrice, attributes, swipeStock, SubscriptionTier.NONE);
    }

    @Transactional
    public Listing createListing(User seller, UUID categoryId, String title, String description, String brand,
                                 ItemCondition condition, String city, String state, String zip,
                                 BigDecimal reservePrice, Map<String, String> attributes, boolean swipeStock,
                                 SubscriptionTier requiredTier) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        Listing listing = listingRepository.save(Listing.builder()
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
                .swipeStock(swipeStock)
                .requiredTier(requiredTier != null ? requiredTier : SubscriptionTier.NONE)
                .build());
        if (attributes != null) {
            attributes.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                    listingAttributeRepository.save(ListingAttribute.builder()
                            .listing(listing).key(key).value(value).build());
                }
            });
        }
        return listing;
    }

    /** Adds an image to a listing the given seller owns; marking it cover unsets any prior cover image. */
    @Transactional
    public ListingImage addImage(User seller, UUID listingId, String url, boolean cover) {
        Listing listing = getListing(listingId);
        if (!listing.getSeller().getId().equals(seller.getId())) {
            throw new BadRequestException("You do not own this listing");
        }
        List<ListingImage> existing = listingImageRepository.findByListing_IdOrderBySortOrderAsc(listingId);
        if (cover) {
            existing.stream().filter(ListingImage::isCover).forEach(img -> {
                img.setCover(false);
                listingImageRepository.save(img);
            });
        }
        return listingImageRepository.save(ListingImage.builder()
                .listing(listing)
                .url(url)
                .sortOrder(existing.size())
                .cover(cover || existing.isEmpty())
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
