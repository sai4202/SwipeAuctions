-- One-shot OTPs for the "Login with OTP" flow, kept separate from otp_verifications (which encodes
-- pending registration-verification state, not a one-time login code).
create table login_otps (
    consumed boolean not null default false,
    created_at timestamp(6) not null,
    expiry timestamp(6) not null,
    last_sent_at timestamp(6),
    updated_at timestamp(6) not null,
    id uuid not null,
    email varchar(255) not null,
    otp varchar(6) not null,
    primary key (id)
);

create index idx_login_otps_email on login_otps(email);
