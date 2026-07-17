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
                                "/api/status",
                                "/actuator/health",
                                "/error",

                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",

                                // User Public APIs
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/verify-email",
                                "/api/auth/verify-mobile",
                                "/api/auth/resend-otp",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",

                                // Admin Public APIs
                                "/api/admin/auth/register",
                                "/api/admin/auth/login",
                                "/api/admin/auth/forgot-password",
                                "/api/admin/auth/reset-password"

                        ).permitAll()

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