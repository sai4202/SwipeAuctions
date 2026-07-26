package com.swipeauctions.event.controller;

import com.swipeauctions.auction.repository.AuctionRepository;
import com.swipeauctions.catalog.entity.Category;
import com.swipeauctions.catalog.repository.CategoryRepository;
import com.swipeauctions.common.exception.ResourceNotFoundException;
import com.swipeauctions.common.util.LoggedInUserUtil;
import com.swipeauctions.event.entity.AuctionEvent;
import com.swipeauctions.event.service.AuctionEventService;
import com.swipeauctions.user.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Seller-created auction events, each scoped to a single category (e.g. Bank Vehicles,
 * Insurance). Browsing lists events first (with an item count); entering one shows its auctions
 * via {@code GET /api/auctions?eventId=...}.
 */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class AuctionEventController {

    private final AuctionEventService auctionEventService;
    private final AuctionRepository auctionRepository;
    private final CategoryRepository categoryRepository;
    private final LoggedInUserUtil loggedInUserUtil;

    @GetMapping
    public List<EventResponse> list(@RequestParam(required = false) String category) {
        return auctionEventService.listEvents(category).stream().map(this::toResponse).toList();
    }

    @GetMapping("/mine")
    public List<EventResponse> mine() {
        User seller = loggedInUserUtil.getCurrentUser();
        return auctionEventService.listMyEvents(seller).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public EventResponse get(@PathVariable UUID id) {
        return toResponse(auctionEventService.getEvent(id));
    }

    @PostMapping
    public EventResponse create(@Valid @RequestBody CreateEventRequest req) {
        User seller = loggedInUserUtil.getCurrentUser();
        Category category = categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        AuctionEvent event = auctionEventService.createEvent(seller, category, req.name(), req.location(),
                req.startTime(), req.closingTime());
        return toResponse(event);
    }

    private EventResponse toResponse(AuctionEvent e) {
        long itemCount = auctionRepository.countByEvent_Id(e.getId());
        return new EventResponse(e.getId(), e.getName(), e.getLocation(), e.getStartTime(),
                e.getClosingTime(), itemCount, e.getSeller().getEmail(),
                e.getCategory().getId(), e.getCategory().getSlug());
    }

    public record CreateEventRequest(
            @NotBlank String name,
            String location,
            @NotNull LocalDateTime startTime,
            @NotNull LocalDateTime closingTime,
            @NotNull UUID categoryId) {}

    public record EventResponse(
            UUID id, String name, String location, LocalDateTime startTime, LocalDateTime closingTime,
            long itemCount, String sellerEmail, UUID categoryId, String categorySlug) {}
}
