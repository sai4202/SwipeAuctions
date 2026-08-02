-- Adds a permission tier on top of admin accounts: SUPER_ADMIN can create new admins, plain ADMIN
-- cannot. Existing admins are grandfathered in as SUPER_ADMIN so nobody loses the ability to create
-- new admins the moment this migration ships — a SUPER_ADMIN can demote others later if desired.

alter table admins add column admin_role varchar(20) not null default 'ADMIN';

update admins set admin_role = 'SUPER_ADMIN';
