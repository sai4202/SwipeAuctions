-- Lets admin mark a membership benefit as requiring extra payment, with its own
-- monthly/quarterly/half-yearly/yearly price (independent of the tier subscription prices).

alter table membership_benefits add column paid boolean not null default false;

create table membership_benefit_prices (
    benefit_id uuid not null,
    billing_cycle varchar(20) not null check (billing_cycle in ('MONTHLY','QUARTERLY','HALF_YEARLY','YEARLY')),
    price numeric(12,2) not null,
    primary key (benefit_id, billing_cycle),
    foreign key (benefit_id) references membership_benefits (id) on delete cascade
);
