package com.swipeauctions.chat.entity;

import com.swipeauctions.chat.enums.ChatSender;
import com.swipeauctions.common.entity.BaseEntity;
import com.swipeauctions.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

/** One message in a support-chat thread. A thread is scoped to one {@code user} — every message in
 *  it, from either side, shares that same user id; {@code sender} says which side actually wrote it. */
@Entity
@Table(name = "chat_messages", indexes = @Index(name = "idx_chat_messages_user", columnList = "user_id, created_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChatSender sender;

    /** Nullable — an attachment-only message (see below) has no caption text. */
    @Column(columnDefinition = "TEXT")
    private String body;

    /** Public /uploads/** URL from StorageProvider — same image/video-only validation as every
     *  other upload in the app (banners, listing photos), reused as-is. Null for a text-only message. */
    @Column(name = "attachment_url", length = 500)
    private String attachmentUrl;

    /** "IMAGE" or "VIDEO", derived from the uploaded file's content type. Null for a text-only message. */
    @Column(name = "attachment_type", length = 20)
    private String attachmentType;

    /** Original filename, shown next to the attachment. Null for a text-only message. */
    @Column(name = "attachment_name")
    private String attachmentName;
}
