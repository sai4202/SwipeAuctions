package com.swipeauctions.common.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.swipeauctions.admin.repository.AdminRepository;
import com.swipeauctions.session.entity.AdminSessions;
import com.swipeauctions.session.entity.UserSessions;
import com.swipeauctions.session.repository.AdminSessionRepository;
import com.swipeauctions.session.repository.UserSessionRepository;
import com.swipeauctions.user.repository.UserRepository;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    private final UserDetailsService userDetailsService;

    private final UserSessionRepository userSessionRepository;

    private final AdminSessionRepository adminSessionRepository;

    private final UserRepository userRepository;

    private final AdminRepository adminRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // Read Authorization header
        final String authHeader = request.getHeader("Authorization");

        // Continue request if JWT token is not present
        if (authHeader == null || !authHeader.startsWith("Bearer "))
        {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract JWT token
        final String jwt = authHeader.substring(7);

        String email;

        String jwtId;

        try {

            // Extract user email from JWT
            email = jwtService.extractUsername(jwt);

            // Extract unique session identifier from JWT
            jwtId = jwtService.extractJwtId(jwt);

        } catch (Exception exception) {

            // Invalid or malformed token
            filterChain.doFilter(request, response);
            return;
        }

        // Validate active session based on account type
        boolean isUser = userRepository.existsByEmail(email);

        boolean isAdmin = adminRepository.existsByEmail(email);

        Object activeSession = null;

        if (isUser)
        {
            activeSession = userSessionRepository.findByJwtIdAndActiveTrue(jwtId).orElse(null);
        }

        else if (isAdmin) {

            activeSession = adminSessionRepository.findByJwtIdAndActiveTrue(jwtId).orElse(null);
        }

        if (activeSession == null)
        {
            // Session is invalid or already logged out
            filterChain.doFilter(request, response);
            return;
        }

        // Authenticate user only if authentication is not already set
        if (email != null
                && SecurityContextHolder
                .getContext()
                .getAuthentication() == null) {

            try {

                // Load user from database
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                // Validate token ownership and expiry
                if (jwtService.validateToken(jwt, userDetails)) {

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(

                                    userDetails,

                                    null,

                                    userDetails.getAuthorities()
                            );

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // Store authenticated user in Security Context
                    SecurityContextHolder
                            .getContext()
                            .setAuthentication(authToken);

                    // Update last activity timestamp
                    if (activeSession instanceof UserSessions userSession) {

                        userSession.setLastActivityTime(java.time.LocalDateTime.now());

                        userSessionRepository.save(userSession);
                    }

                    else if (activeSession instanceof AdminSessions adminSession) {

                        adminSession.setLastActivityTime(java.time.LocalDateTime.now());

                        adminSessionRepository.save(adminSession);
                    }
                }

            } catch (Exception exception) {

                // User not found or token validation failed
                filterChain.doFilter(request, response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}