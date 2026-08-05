-- Tracks when PaymentReminderScheduler last emailed a winner about an unpaid settlement remainder,
-- so it can re-remind on an interval without spamming every tick. Null = never reminded.

alter table auctions add column settlement_reminder_sent_at timestamp;
