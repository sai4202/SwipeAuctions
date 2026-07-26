-- Remove the "Industrial" category entirely (superseded by the new admin category-management
-- feature — admins can now add any category themselves instead of relying on a fixed seeded set).
-- None of the catalog/auction FKs below are ON DELETE CASCADE, so every dependent row for an
-- industrial listing/auction must be deleted in FK-safe order before the category row itself.

DELETE FROM disputes WHERE auction_id IN (
    SELECT a.id FROM auctions a
    JOIN listings l ON l.id = a.listing_id
    JOIN categories c ON c.id = l.category_id
    WHERE c.slug = 'industrial'
);

DELETE FROM sale_proceeds_holds WHERE auction_id IN (
    SELECT a.id FROM auctions a
    JOIN listings l ON l.id = a.listing_id
    JOIN categories c ON c.id = l.category_id
    WHERE c.slug = 'industrial'
);

DELETE FROM bid_eligibility_holds WHERE auction_id IN (
    SELECT a.id FROM auctions a
    JOIN listings l ON l.id = a.listing_id
    JOIN categories c ON c.id = l.category_id
    WHERE c.slug = 'industrial'
);

DELETE FROM bids WHERE auction_id IN (
    SELECT a.id FROM auctions a
    JOIN listings l ON l.id = a.listing_id
    JOIN categories c ON c.id = l.category_id
    WHERE c.slug = 'industrial'
);

DELETE FROM auctions WHERE listing_id IN (
    SELECT l.id FROM listings l
    JOIN categories c ON c.id = l.category_id
    WHERE c.slug = 'industrial'
);

DELETE FROM listing_attributes WHERE listing_id IN (
    SELECT l.id FROM listings l
    JOIN categories c ON c.id = l.category_id
    WHERE c.slug = 'industrial'
);

DELETE FROM listing_images WHERE listing_id IN (
    SELECT l.id FROM listings l
    JOIN categories c ON c.id = l.category_id
    WHERE c.slug = 'industrial'
);

DELETE FROM listings WHERE category_id IN (SELECT id FROM categories WHERE slug = 'industrial');

DELETE FROM category_attribute_defs WHERE category_id IN (SELECT id FROM categories WHERE slug = 'industrial');

DELETE FROM categories WHERE slug = 'industrial';
