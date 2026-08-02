-- Adds ADMIN_CREATED to admin_audit_log's action check constraint (V23) — the Java AuditAction
-- enum gained this value for the new Create Admin feature but the DB-level check was never updated,
-- which made every ADMIN_CREATED insert fail with a 23514 constraint violation.

alter table admin_audit_log drop constraint admin_audit_log_action_check;

alter table admin_audit_log add constraint admin_audit_log_action_check check (action in (
    'USER_SUSPENDED','USER_REACTIVATED','HOLD_RELEASED','AUCTION_FORCE_CLOSED','AUCTION_MODIFIED',
    'LISTING_REQUIRED_TIER_CHANGED','LISTING_CATEGORY_CHANGED','DISPUTE_RESOLVED','CATEGORY_CREATED',
    'CATEGORY_ATTRIBUTE_ADDED','KYC_APPROVED','KYC_REJECTED','STOCK_LISTING_CREATED','STOCK_BULK_IMPORTED',
    'REGISTRATION_FEE_UPDATED','SUBSCRIPTION_PRICES_UPDATED','MEMBERSHIP_BENEFIT_ADDED',
    'MEMBERSHIP_BENEFIT_TIERS_UPDATED','MEMBERSHIP_BENEFIT_REMOVED','ADMIN_CREATED'
));
