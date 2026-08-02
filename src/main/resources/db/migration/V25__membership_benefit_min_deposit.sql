-- Lets admin gate a membership benefit behind a minimum wallet deposit instead of (or as an
-- alternative to) charging extra for it — e.g. "Pan India Access" unlocked at a ₹50,000 deposit.
-- Mutually exclusive with the `paid` flag, enforced in MembershipBenefitService#addBenefit.

alter table membership_benefits add column min_deposit numeric(12,2);
