-- V28 — Category banners: promotional image strips admin can attach to a category (or leave
-- platform-wide when category_id is null), shown on the homepage/category pages. DDL generated
-- from the CategoryBanner entity so ddl-auto=validate matches.

create table category_banners (
    id uuid not null,
    category_id uuid,
    image_url varchar(500) not null,
    link_url varchar(500),
    title varchar(200),
    sort_order integer not null default 0,
    active boolean not null default true,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    primary key (id)
);

create index idx_category_banners_category on category_banners (category_id);
create index idx_category_banners_active on category_banners (active);

alter table if exists category_banners
    add constraint fk_category_banners_category foreign key (category_id) references categories;
