package com.swipeauctions.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.swipeauctions.common.security.jwt.JwtAccessDeniedHandler;
import com.swipeauctions.common.security.jwt.JwtAuthenticationEntryPoint;
import com.swipeauctions.common.security.jwt.JwtAuthenticationFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    // Comma-separated in app.cors.allowed-origins (APP_CORS_ALLOWED_ORIGINS) — the deployed
    // frontend runs on a different origin than this API (e.g. Vercel vs. Railway), so without this
    // every browser request would be blocked by CORS despite a valid JWT.
    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception
    {
        http
                // Disable CSRF for REST APIs
                .csrf(csrf -> csrf.disable())

                // Browser calls from the deployed frontend's own origin (see corsConfigurationSource above)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

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

                                // Razorpay calls this directly — no JWT, auth is the X-Razorpay-Signature header
                                "/api/webhooks/razorpay"

                        ).permitAll()

                        // Seller-only: list my own auction events (must be checked before the public GET rule below)
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/events/mine")
                        .authenticated()

                        // Buyer-only: list auctions I've won (must be checked before the public GET rule below)
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/auctions/mine/won")
                        .authenticated()

                        // Registration fee / subscription pricing must be visible before an account even
                        // exists (RegisterPage shows it pre-signup).
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/settings/**")
                        .permitAll()
                        .requestMatchers("/ws/**")
                        .permitAll()

                        // The auction catalogue/list is public — browsing doesn't require sign-in,
                        // matching the frontend's public /auctions route. The detail endpoint
                        // (GET /api/auctions/{id}) is deliberately NOT matched here: it falls through to
                        // anyRequest().authenticated() below, and AuctionController.get() additionally
                        // requires registrationFeePaid before returning full details. Everything else
                        // under auctions/events (create, register, bid, "my wins", etc.) also stays
                        // behind the same catch-all.
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/auctions")
                        .permitAll()

                        // User Session APIs — DEALER included so dealers can self-manage/logout their
                        // own devices too, not just USER (fails closed before this fix: DEALER got a
                        // blanket 403). See Findings_pendings.md (Low/Informational).
                        .requestMatchers("/api/sessions/**")
                        .hasAnyRole("USER", "DEALER")

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