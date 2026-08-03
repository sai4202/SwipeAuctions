-- Lets a chat message carry an image/video attachment (with an optional caption) instead of only
-- plain text — see ChatMessage entity / ChatController#sendAttachment. body becomes optional since
-- an attachment-only message has no caption text.

alter table chat_messages alter column body drop not null;
alter table chat_messages add column attachment_url varchar(500);
alter table chat_messages add column attachment_type varchar(20);
alter table chat_messages add column attachment_name varchar(255);
