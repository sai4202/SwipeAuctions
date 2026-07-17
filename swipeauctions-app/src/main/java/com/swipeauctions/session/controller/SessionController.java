package com.swipeauctions.session.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.swipeauctions.common.response.ApiResponse;
import com.swipeauctions.session.dtos.SessionResponseDTO;
import com.swipeauctions.session.service.SessionService;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    // Fetch all active sessions of the logged-in user
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<SessionResponseDTO>>> getActiveSessions(
            Principal principal
    ) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Active sessions fetched successfully",
                        sessionService.getActiveSessions(principal.getName())
                )
        );
    }

    // Logout a specific device/session
    @PostMapping("/logout/{sessionId}")
    public ResponseEntity<ApiResponse<String>> logoutSession(

            @PathVariable
            UUID sessionId,

            Principal principal
    ) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        sessionService.logoutSession(
                                sessionId,
                                principal.getName()
                        ),
                        null
                )
        );
    }

    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<String>> logoutAllSessions(Principal principal)
    {
        return ResponseEntity.ok(ApiResponse.success(
                sessionService.logoutAllSessions(principal.getName()), null)
        );
    }
}