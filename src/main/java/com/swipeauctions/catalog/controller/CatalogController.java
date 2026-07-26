package com.swipeauctions.catalog.controller;

import com.swipeauctions.catalog.entity.Category;
import com.swipeauctions.catalog.entity.Listing;
import com.swipeauctions.catalog.entity.ListingImage;
import com.swipeauctions.catalog.enums.AttributeValueType;
import com.swipeauctions.catalog.enums.ItemCondition;
import com.swipeauctions.catalog.enums.ListingStatus;
import com.swipeauctions.catalog.service.CatalogService;
import com.swipeauctions.storage.service.StorageProvider;
import com.swipeauctions.common.exception.BadRequestException;
import com.swipeauctions.common.util.LoggedInUserUtil;
import com.swipeauctions.user.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Public browse of categories/listings; authenticated create. */
@RestController
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;
    private final StorageProvider storageProvider;
    private final LoggedInUserUtil loggedInUserUtil;

    @GetMapping("/api/categories")
    public List<CategoryResponse> categories() {
        return catalogService.listCategories().stream().map(CatalogController::toCategory).toList();
    }

    /** Live browse-page filters for a category — defs + the real distinct values seen for each. */
    @GetMapping("/api/categories/{id}/filters")
    public List<CategoryFilterResponse> categoryFilters(@PathVariable UUID id) {
        return catalogService.listPublicFilters(id).stream()
                .map(f -> new CategoryFilterResponse(f.key(), f.label(), f.valueType(), f.options()))
                .toList();
    }

    @GetMapping("/api/listings")
    public List<ListingResponse> listings(@RequestParam(required = false) ListingStatus status) {
        return catalogService.listListings(status).stream().map(CatalogController::toListing).toList();
    }

    @GetMapping("/api/listings/{id}")
    public ListingResponse listing(@PathVariable UUID id) {
        return toListing(catalogService.getListing(id));
    }

    /** Create a listing (draft, before an auction exists for it). Requires completed KYC — same gate as bidding. */
    @PostMapping("/api/listings")
    public ListingResponse createListing(@Valid @RequestBody CreateListingRequest req) {
        User seller = loggedInUserUtil.getCurrentUser();
        if (!Boolean.TRUE.equals(seller.getKycCompleted())) {
            throw new BadRequestException("Complete your KYC verification before creating a listing.");
        }
        Listing listing = catalogService.createListing(seller, req.categoryId(), req.title(), req.description(),
                req.brand(), req.condition(), req.city(), req.state(), req.zip(), req.reservePrice(),
                req.attributes());
        return toListing(listing);
    }

    @PostMapping(value = "/api/listings/{id}/images", consumes = "multipart/form-data")
    public ImageResponse addImage(@PathVariable UUID id, @RequestParam MultipartFile file,
                                   @RequestParam(defaultValue = "false") boolean cover) {
        User seller = loggedInUserUtil.getCurrentUser();
        String url = storageProvider.store(file, "listings/" + id);
        ListingImage image = catalogService.addImage(seller, id, url, cover);
        return new ImageResponse(image.getId(), image.getUrl(), image.isCover());
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

    public record CategoryResponse(UUID id, String name, String slug, UUID parentId) {}

    public record CategoryFilterResponse(String key, String label, AttributeValueType valueType, List<String> options) {}

    public record CreateListingRequest(
            @NotNull UUID categoryId,
            @NotBlank String title,
            String description,
            String brand,
            ItemCondition condition,
            String city,
            String state,
            String zip,
            @NotNull BigDecimal reservePrice,
            Map<String, String> attributes) {}

    public record ListingResponse(
            UUID id, String title, String brand, ItemCondition condition,
            UUID categoryId, String categoryName, String city, String state,
            BigDecimal reservePrice, ListingStatus status, String sellerEmail) {}

    public record ImageResponse(UUID id, String url, boolean cover) {}
}
