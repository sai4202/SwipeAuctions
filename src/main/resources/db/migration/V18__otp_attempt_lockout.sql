-- Wrong-OTP guesses on verify-email/verify-mobile/email-change/mobile-change had no attempt limit,
-- so a 6-digit OTP was brute-forceable within its 10-minute validity window. Track attempts per OTP
-- record so the service layer can lock out after too many wrong guesses.
alter table otp_verifications add column email_otp_attempts int not null default 0;
alter table otp_verifications add column mobile_otp_attempts int not null default 0;

alter table user_update_requests add column email_otp_attempts int not null default 0;
alter table user_update_requests add column mobile_otp_attempts int not null default 0;
