package com.swipeauctions.common.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

@Service
public class JwtService {

    /*
     * Secret key used to sign and validate JWT tokens.
     */
    @Value("${jwt.secret}")
    private String secret;

    /*
     * Token validity period in milliseconds.
     */
    @Value("${jwt.expiration}")
    private long expiration;

    /*
     * Generates unique JWT ID for session tracking.
     */
    public String generateJwtId()
    {
        return UUID.randomUUID().toString();
    }

    /*
     * Generates JWT token for the authenticated user.
     */
    public String generateToken(
            String email,
            String jwtId
    ) {

        return Jwts.builder()
                .id(jwtId)
                .subject(email)
                .issuedAt(new Date())
                .expiration(
                        new Date(
                                System.currentTimeMillis()
                                        + expiration
                        )
                )
                .signWith(getSignKey())
                .compact();
    }

    /*
     * Extracts the user's email from the token.
     */
    public String extractUsername(
            String token
    ) {

        return extractClaim(
                token,
                Claims::getSubject
        );
    }

    /*
     * Extracts JWT ID from token.
     */
    public String extractJwtId(
            String token
    ) {

        return extractClaim(
                token,
                Claims::getId
        );
    }

    /*
     * Extracts token expiration date.
     */
    public Date extractExpiration(
            String token
    ) {

        return extractClaim(
                token,
                Claims::getExpiration
        );
    }

    /*
     * Generic method used to read claims
     * from the token payload.
     */
    public <T> T extractClaim(
            String token,
            Function<Claims, T> resolver
    ) {

        Claims claims =
                extractAllClaims(token);

        return resolver.apply(claims);
    }

    /*
     * Returns all claims stored inside JWT.
     */
    public Claims extractAllClaims(
            String token
    ) {

        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /*
     * Validates token ownership and expiry.
     */
    public boolean validateToken(
            String token,
            UserDetails userDetails
    ) {

        return extractUsername(token)
                .equals(userDetails.getUsername())
                && !isTokenExpired(token);
    }

    /*
     * Checks whether token has expired.
     */
    private boolean isTokenExpired(
            String token
    ) {

        return extractExpiration(token)
                .before(new Date());
    }

    /*
     * Creates signing key from configured secret.
     */
    private SecretKey getSignKey() {

        byte[] keyBytes =
                Decoders.BASE64.decode(secret);

        return Keys.hmacShaKeyFor(keyBytes);
    }
}