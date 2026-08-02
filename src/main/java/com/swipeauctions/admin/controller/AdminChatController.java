package com.swipeauctions.admin.controller;

import com.swipeauctions.chat.controller.ChatController;
import com.swipeauctions.chat.entity.ChatMessage;
import com.swipeauctions.chat.enums.ChatSender;
import com.swipeauctions.chat.repository.ChatMessageRepository;
import com.swipeauctions.common.exception.ResourceNotFoundException;
import com.swipeauctions.common.util.LoggedInUserUtil;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.user.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Admin side of the same support-chat threads {@link ChatController} exposes to users — every
 *  conversation, ranked by most recent activity, plus the full history of any one thread. Kept
 *  separate from the already-large {@link AdminController}, same as {@code AdminAuditLogController}. */
@RestController
@RequestMapping("/api/admin/chat")
@RequiredArgsConstructor
public class AdminChatController {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final LoggedInUserUtil loggedInUserUtil;

    /** One row per user who has ever sent/received a chat message, newest activity first. */
    @GetMapping("/conversations")
    public List<ConversationResponse> conversations() {
        Map<UUID, List<ChatMessage>> byUser = chatMessageRepository.findAllByOrderByCreatedAtAsc().stream()
                .collect(Collectors.groupingBy(m -> m.getUser().getId(), java.util.LinkedHashMap::new, Collectors.toList()));

        return byUser.values().stream()
                .map(messages -> {
                    ChatMessage last = messages.get(messages.size() - 1);
                    return new ConversationResponse(last.getUser().getId(), last.getUser().getEmail(),
                            last.getBody(), last.getSender(), last.getCreatedAt(), messages.size());
                })
                .sorted(Comparator.comparing(ConversationResponse::lastMessageAt).reversed())
                .toList();
    }

    @GetMapping("/conversations/{userId}/messages")
    public List<ChatController.ChatMessageResponse> messages(@PathVariable UUID userId) {
        return chatMessageRepository.findByUser_IdOrderByCreatedAtAsc(userId).stream()
                .map(ChatController::toResponse).toList();
    }

    @PostMapping("/conversations/{userId}/messages")
    public ChatController.ChatMessageResponse reply(@PathVariable UUID userId, @Valid @RequestBody SendReplyRequest req) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        ChatMessage saved = chatMessageRepository.save(
                ChatMessage.builder().user(user).sender(ChatSender.ADMIN).body(req.body().trim()).build());
        return ChatController.toResponse(saved);
    }

    public record ConversationResponse(UUID userId, String userEmail, String lastMessage, ChatSender lastSender,
                                       LocalDateTime lastMessageAt, int messageCount) {}

    public record SendReplyRequest(@NotBlank(message = "Message can't be empty") String body) {}
}
