package com.swipeauctions.dispute.service;

import com.swipeauctions.auction.entity.Auction;
import com.swipeauctions.auction.repository.AuctionRepository;
import com.swipeauctions.common.exception.BadRequestException;
import com.swipeauctions.common.exception.ResourceNotFoundException;
import com.swipeauctions.dispute.entity.Dispute;
import com.swipeauctions.dispute.enums.DisputeStatus;
import com.swipeauctions.dispute.repository.DisputeRepository;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.wallet.entity.SaleProceedsHold;
import com.swipeauctions.wallet.enums.ProceedsStatus;
import com.swipeauctions.wallet.repository.SaleProceedsHoldRepository;
import com.swipeauctions.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Dispute tracking: raise, list, resolve. Resolution can release or refund the auction's escrowed
 * seller proceeds ({@link SaleProceedsHold}) — only while still ACTIVE (see {@link
 * WalletService#refundSaleProceeds}); if it already auto-released before the dispute was resolved,
 * the financial side is a no-op and only the dispute status changes (a known, documented gap).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DisputeService {

    private final DisputeRepository disputeRepository;
    private final AuctionRepository auctionRepository;
    private final SaleProceedsHoldRepository proceedsRepository;
    private final WalletService walletService;

    @Transactional
    public Dispute raise(User raisedBy, UUID auctionId, String reason) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new ResourceNotFoundException("Auction not found"));
        return disputeRepository.save(Dispute.builder()
                .auction(auction).raisedBy(raisedBy).reason(reason).build());
    }

    @Transactional(readOnly = true)
    public Page<Dispute> list(DisputeStatus status, Pageable pageable) {
        return status != null ? disputeRepository.findByStatus(status, pageable) : disputeRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<Dispute> listByUser(UUID userId) {
        return disputeRepository.findByRaisedBy_Id(userId);
    }

    @Transactional(readOnly = true)
    public Dispute get(UUID id) {
        return disputeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute not found"));
    }

    /**
     * @param refundBuyer true = dispute upheld, reverse escrowed proceeds back to the buyer;
     *                    false = dispute not upheld (or informational), release proceeds to the seller.
     *                    Either way, no-ops financially if there's no ACTIVE escrow for this auction
     *                    (auction not settled yet, or proceeds already auto-released).
     */
    @Transactional
    public Dispute resolve(UUID id, String adminNotes, boolean refundBuyer) {
        Dispute d = get(id);
        if (d.getStatus() == DisputeStatus.RESOLVED) {
            throw new BadRequestException("This dispute has already been resolved");
        }

        proceedsRepository.findByAuction_Id(d.getAuction().getId())
                .filter(hold -> hold.getStatus() == ProceedsStatus.ACTIVE)
                .ifPresentOrElse(
                        hold -> {
                            if (refundBuyer) {
                                walletService.refundSaleProceeds(hold, d.getAuction().getCurrentWinner());
                            } else {
                                walletService.releaseSaleProceeds(hold);
                            }
                        },
                        () -> log.info("[dispute] resolving {} with no ACTIVE sale-proceeds escrow "
                                + "(auction not settled, or proceeds already released) — status-only resolution", id)
                );

        d.setStatus(DisputeStatus.RESOLVED);
        d.setAdminNotes(adminNotes);
        d.setResolvedAt(LocalDateTime.now());
        return disputeRepository.save(d);
    }
}
