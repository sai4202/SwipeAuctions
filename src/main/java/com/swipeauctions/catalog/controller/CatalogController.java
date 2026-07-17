package com.swipeauctions.catalog.controller;

import com.swipeauctions.catalog.entity.Category;
import com.swipeauctions.catalog.entity.Listing;
import com.swipeauctions.catalog.enums.ItemCondition;
import com.swipeauctions.catalog.enums.ListingStatus;
import com.swipeauctions.catalog.service.CatalogService;
import com.swipeauctions.common.util.LoggedInUserUtil;
import com.swipeauctions.user.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Public browse of categories/listings; authenticated create. */
@RestController
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;
    private final LoggedInUserUtil loggedInUserUtil;

    @GetMapping("/api/categories")
    public List<CategoryResponse> categories() {
        return catalogService.listCategories().stream().map(CatalogController::toCategory).toList();
    }

    @PostMapping("/api/categories")
    public CategoryResponse createCategory(@Valid @RequestBody CreateCategoryRequest req) {
        return toCategory(catalogService.createCategory(req.name(), req.slug(), req.parentId()));
    }

    @GetMapping("/api/listings")
    public List<ListingResponse> listings(@RequestParam(required = false) ListingStatus status) {
        return catalogService.listListings(status).stream().map(CatalogController::toListing).toList();
    }

    @GetMapping("/api/listings/{id}")
    public ListingResponse listing(@PathVariable UUID id) {
        return toListing(catalogService.getListing(id));
    }

    @PostMapping("/api/listings")
    public ListingResponse createListing(@Valid @RequestBody CreateListingRequest req) {
        User seller = loggedInUserUtil.getCurrentUser();
        Listing listing = catalogService.createListing(seller, req.categoryId(), req.title(), req.description(),
                req.brand(), req.condition(), req.city(), req.state(), req.zip(), req.reservePrice());
        return toListing(listing);
    }

    static CategoryResponse toCategory(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getSlug(),
                c.getParent() != null ? c.getParent().getId() : null);
    }

    static ListingResponse toListing(Listing l) {
        return new ListingResponse(l.getId(), l.getTitle(), l.getBrand(), l.getCondition(),
                l.getCategory().getId(), l.getCategory().getName(), l.getCity(), l.getState(),
                l.getReservePrice(), l.getStatus(), l.getSeller().getEmail());
    }

    public record CreateCategoryRequest(@NotBlank String name, @NotBlank String slug, UUID parentId) {}

    public record CategoryResponse(UUID id, String name, String slug, UUID parentId) {}

    public record CreateListingRequest(
            @NotNull UUID categoryId,
            @NotBlank String title,
            String description,
            String brand,
            ItemCondition condition,
            String city,
            String state,
            String zip,
            @NotNull BigDecimal reservePrice) {}

    public record ListingResponse(
            UUID id, String title, String brand, ItemCondition condition,
            UUID categoryId, String categoryName, String city, String state,
            BigDecimal reservePrice, ListingStatus status, String sellerEmail) {}
}
