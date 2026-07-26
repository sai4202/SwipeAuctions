-- Generalize seller-created auction events beyond Bank Assets: link each event to a category so
-- multiple categories (Bank Vehicles, Insurance, ...) can use the same events->items browsing flow.
ALTER TABLE auction_events ADD COLUMN category_id UUID REFERENCES categories(id);

-- Backfill existing events (all were Bank Assets, the only category events supported until now).
UPDATE auction_events e SET category_id = (SELECT id FROM categories WHERE slug = 'bank-assets')
WHERE category_id IS NULL;

-- Rename Bank Assets -> Bank Vehicles (data lives in DevDataSeeder; this is a no-op if the row
-- doesn't exist yet, e.g. a fresh database that hasn't run the dev seeder).
UPDATE categories SET name = 'Bank Vehicles', slug = 'bank-vehicles' WHERE slug = 'bank-assets';

ALTER TABLE auction_events ALTER COLUMN category_id SET NOT NULL;
CREATE INDEX idx_auction_events_category ON auction_events(category_id);
