-- Wins now require explicit admin approval before the winner can pay the settlement remainder
-- (AuctionService#approveWin/rejectWin). Backfill: auctions already fully settled are treated as
-- already-approved so a past, done deal doesn't suddenly show "Approval Pending".

alter table auctions add column win_approved boolean not null default false;
alter table auctions add column win_approved_at timestamp;

update auctions set win_approved = true, win_approved_at = updated_at where settlement_paid = true;
