package com.swipeauctions.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.swipeauctions.common.security.jwt.JwtAccessDeniedHandler;
import com.swipeauctions.common.security.jwt.JwtAuthenticationEntryPoint;
import com.swipeauctions.common.security.jwt.JwtAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception
    {
        http
                // Disable CSRF for REST APIs
                .csrf(csrf -> csrf.disable())

                // Stateless JWT Authentication
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Custom Security Exception Handling
                .exceptionHandling(exception -> exception

                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)

                        .accessDeniedHandler(jwtAccessDeniedHandler)
                )

                // Authorization Rules
                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(

                                // Public landing page, health/status, static assets
                                "/",
                                "/index.html",
                                "/favicon.ico",
                                "/css/**",
                                "/js/**",
                                "/assets/**",
                                "/uploads/**",
                                "/api/status",
                                "/actuator/health",
                                "/error",

                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",

                                // User Public APIs
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/logout-device",
                                "/api/auth/verify-email",
                                "/api/auth/verify-mobile",
                                "/api/auth/resend-otp",
                                "/api/auth/login/otp/request",
                                "/api/auth/login/otp/verify",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",

                                // Admin Public APIs
                                // NOTE: /api/admin/auth/register is deliberately NOT here — it's covered by the
                                // /api/admin/** -> hasRole("ADMIN") rule below, so only an already-authenticated
                                // admin can create another admin. Bootstrap the first admin via a trusted
                                // out-of-band path (DB seed/migration), never a public endpoint.
                                "/api/admin/auth/login",
                                "/api/admin/auth/forgot-password",
                                "/api/admin/auth/reset-password",

                                // Stripe calls this directly — no JWT, auth is the Stripe-Signature header
                                "/api/webhooks/stripe"

                        ).permitAll()

                        // Seller-only: list my own auction events (must be checked before the public GET rule below)
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/events/mine")
                        .authenticated()

                        // Buyer-only: list auctions I've won (must be checked before the public GET rule below)
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/auctions/mine/won")
                        .authenticated()

                        // Public browse (read-only catalog + auctions + events) and the WebSocket endpoint
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/categories/**", "/api/listings/**", "/api/auctions/**", "/api/events/**",
                                "/api/settings/**")
                        .permitAll()
                        .requestMatchers("/ws/**")
                        .permitAll()

                        // User Session APIs
                        .requestMatchers("/api/sessions/**")
                        .hasRole("USER")

                        // Admin Protected APIs
                        .requestMatchers("/api/admin/**")
                        .hasRole("ADMIN")

                        .anyRequest()
                        .authenticated()
                )

                // JWT Filter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception
    {
        return configuration.getAuthenticationManager();
    }
}