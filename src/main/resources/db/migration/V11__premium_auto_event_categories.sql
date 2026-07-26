-- Extend the event-grouped categories (Bank Vehicles, Insurance) with two more, matching the
-- CarTrade-style "Events" browse UI pill bar: Premium and Auto.
-- categories.id/created_at/updated_at have no DB-side default (normally filled by
-- BaseEntity's @PrePersist via Hibernate) so a raw-SQL insert must supply them explicitly.
INSERT INTO categories (id, created_at, updated_at, name, slug)
VALUES
    (gen_random_uuid(), now(), now(), 'Premium', 'premium'),
    (gen_random_uuid(), now(), now(), 'Auto', 'auto')
ON CONFLICT (slug) DO NOTHING;
