package com.swipeauctions.bidding.config;

import com.swipeauctions.common.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Date;

/**
 * Reads the JWT sent as a STOMP CONNECT header ({@code Authorization: Bearer <token>}) and, if
 * valid, attaches a Principal to the session so Spring can route {@code /user/queue/...}
 * destinations to this user. Anonymous connections are left as-is — only authenticated features
 * (personal notifications) need this; the public /topic/auctions/{id} broadcasts don't.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7);
                try {
                    String email = jwtService.extractUsername(token);
                    if (jwtService.extractExpiration(token).after(new Date())) {
                        Principal principal = () -> email;
                        accessor.setUser(principal);
                    }
                } catch (Exception e) {
                    log.debug("[ws] ignoring invalid JWT on STOMP CONNECT: {}", e.getMessage());
                }
            }
        }
        return message;
    }
}
