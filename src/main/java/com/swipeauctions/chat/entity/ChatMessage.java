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

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;
}
