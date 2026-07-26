-- KYC no longer auto-approves on submission (see KycServiceImpl) — submissions land in a real
-- PENDING state reviewed by an admin. provider/provider_reference_id/provider_raw_response let a
-- real Indian eKYC vendor plug in later via KycVerificationProvider without another migration;
-- 'MANUAL' is the only provider today (admin-review queue).
alter table kyc_verifications add column provider varchar(50) not null default 'MANUAL';
alter table kyc_verifications add column provider_reference_id varchar(255);
alter table kyc_verifications add column provider_raw_response text;
alter table kyc_verifications add column reviewed_by varchar(255);

create index idx_kyc_verifications_status on kyc_verifications (status);
