package com.swipeauctions.chat.controller;

import com.swipeauctions.chat.entity.ChatMessage;
import com.swipeauctions.chat.enums.ChatSender;
import com.swipeauctions.chat.repository.ChatMessageRepository;
import com.swipeauctions.common.util.LoggedInUserUtil;
import com.swipeauctions.user.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** A signed-in user's own support-chat thread with the admin team. Simple poll-and-refresh model
 *  (frontend re-fetches on an interval while the chat panel is open) rather than a live WebSocket
 *  push — reliable and easy to reason about at this app's message volume; a real-time upgrade later
 *  wouldn't need any API shape changes. */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatMessageRepository chatMessageRepository;
    private final LoggedInUserUtil loggedInUserUtil;

    @GetMapping("/messages")
    public List<ChatMessageResponse> myMessages() {
        User user = loggedInUserUtil.getCurrentUser();
        return chatMessageRepository.findByUser_IdOrderByCreatedAtAsc(user.getId()).stream()
                .map(ChatController::toResponse).toList();
    }

    @PostMapping("/messages")
    public ChatMessageResponse send(@Valid @RequestBody SendMessageRequest req) {
        User user = loggedInUserUtil.getCurrentUser();
        ChatMessage saved = chatMessageRepository.save(
                ChatMessage.builder().user(user).sender(ChatSender.USER).body(req.body().trim()).build());
        return toResponse(saved);
    }

    public static ChatMessageResponse toResponse(ChatMessage m) {
        return new ChatMessageResponse(m.getId(), m.getSender(), m.getBody(), m.getCreatedAt());
    }

    public record SendMessageRequest(@NotBlank(message = "Message can't be empty") String body) {}

    public record ChatMessageResponse(UUID id, ChatSender sender, String body, LocalDateTime createdAt) {}
}
