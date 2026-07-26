-- AuctionStatus.RESERVE_NOT_MET was renamed to UNSOLD across the codebase. Drop whatever the
-- inline CHECK constraint from V5 ended up auto-named (varchar column, not the native auction_status
-- enum, so there's no ALTER TYPE ... RENAME VALUE shortcut here) before updating existing rows.
do $$
declare
    con record;
begin
    for con in
        select conname from pg_constraint
        where conrelid = 'auctions'::regclass
          and contype = 'c'
          and pg_get_constraintdef(oid) like '%RESERVE_NOT_MET%'
    loop
        execute format('alter table auctions drop constraint %I', con.conname);
    end loop;
end $$;

update auctions set status = 'UNSOLD' where status = 'RESERVE_NOT_MET';

alter table auctions add constraint auctions_status_check
    check (status in ('SCHEDULED', 'OPEN', 'CLOSED', 'UNSOLD', 'CANCELLED'));
