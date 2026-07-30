-- Hard backstop against the double-click register-to-bid race (Findings_pendings.md #5): a bidder
-- double-clicking "Register to bid" could pass the app-level "already registered" check twice
-- before either insert committed, debiting the wallet's held balance twice while only one hold row
-- ever gets tracked/released — 1x EMD permanently stranded. App-level locking (WalletService.placeHold
-- now locks the auction row for the whole call, serializing concurrent registrations on the same
-- auction) closes the practical race, but a unique constraint is the DB-level guarantee that no
-- code path — this one or a future one — can ever create two hold rows for the same (auction,
-- bidder) pair.
create unique index idx_bid_eligibility_hold_auction_bidder_unique
    on bid_eligibility_holds (auction_id, bidder_id);
