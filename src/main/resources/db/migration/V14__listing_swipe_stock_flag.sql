-- Explicit per-listing flag for the Swipe Stock page, independent of who the seller is (an admin
-- adding stock always attributes it to the platform seller account, but only items with this flag
-- set show up on /swipe-stock — everything still shows on the normal /auctions browse regardless).
alter table listings add column swipe_stock boolean not null default false;
create index idx_listings_swipe_stock on listings (swipe_stock);
