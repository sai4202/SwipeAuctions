package com.swipeauctions.dispute.controller;

import com.swipeauctions.common.util.LoggedInUserUtil;
import com.swipeauctions.dispute.entity.Dispute;
import com.swipeauctions.dispute.enums.DisputeStatus;
import com.swipeauctions.dispute.service.DisputeService;
import com.swipeauctions.user.entity.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Any authenticated user can raise a dispute against an auction and see their own. */
@RestController
@RequestMapping("/api/disputes")
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;
    private final LoggedInUserUtil loggedInUserUtil;

    @PostMapping
    public DisputeResponse raise(@Valid @RequestBody RaiseDisputeRequest req) {
        User user = loggedInUserUtil.getCurrentUser();
        return toResponse(disputeService.raise(user, req.auctionId(), req.reason()));
    }

    @GetMapping("/mine")
    public List<DisputeResponse> mine() {
        User user = loggedInUserUtil.getCurrentUser();
        return disputeService.listByUser(user.getId()).stream().map(DisputeController::toResponse).toList();
    }

    public static DisputeResponse toResponse(Dispute d) {
        return new DisputeResponse(d.getId(), d.getAuction().getId(), d.getAuction().getListing().getTitle(),
                d.getRaisedBy().getEmail(), d.getReason(), d.getStatus(), d.getAdminNotes(),
                d.getResolvedAt(), d.getCreatedAt());
    }

    public record RaiseDisputeRequest(@NotNull UUID auctionId, @NotBlank String reason) {}

    public record DisputeResponse(
            UUID id, UUID auctionId, String auctionTitle, String raisedByEmail, String reason,
            DisputeStatus status, String adminNotes, LocalDateTime resolvedAt, LocalDateTime createdAt) {}
}
