-- One-time platform registration fee (amount config already exists — see
-- V16__registration_fee_and_subscriptions.sql / PlatformSettingsService) becomes an enforced,
-- per-user gate: a new signup must pay it before viewing an auction's detail page or bidding
-- (AuctionController.get / BidService.placeBid). Existing accounts are grandfathered — only users
-- who register after this migration are subject to the wall.
alter table users add column registration_fee_paid boolean not null default false;
alter table users add column registration_fee_paid_at timestamp(6);

update users set registration_fee_paid = true, registration_fee_paid_at = now();
