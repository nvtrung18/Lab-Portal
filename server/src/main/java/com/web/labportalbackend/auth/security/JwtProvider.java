package com.web.labportalbackend.auth.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * Utility component for JWT token operations:
 * <ul>
 *   <li>Generate access & refresh tokens</li>
 *   <li>Validate tokens</li>
 *   <li>Extract claims (username, roles)</li>
 * </ul>
 */
@Slf4j
@Component
public class JwtProvider {

    private final SecretKey signingKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Chưa cấu hình JWT_SECRET.");
        }
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    /**
     * Generate an access token from a fully authenticated Spring Security principal.
     */
    public String generateAccessToken(Authentication authentication) {
        String roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        return buildToken(authentication.getName(), roles, accessTokenExpiration);
    }

    /**
     * Generate an access token directly from username and roles string.
     */
    public String generateAccessToken(String username, String roles) {
        return buildToken(username, roles, accessTokenExpiration);
    }

    /**
     * Generate a refresh token (longer-lived, no roles claim).
     */
    public String generateRefreshToken(String username) {
        return buildToken(username, null, refreshTokenExpiration);
    }

    /**
     * Extract the username (subject) from a token.
     */
    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Validate a token: checks signature, expiration, and structure.
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty: {}", e.getMessage());
        } catch (JwtException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
        }
        return false;
    }

    // ---- internal helpers ----

    private String buildToken(String subject, String roles, long expirationMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        JwtBuilder builder = Jwts.builder()
                .subject(subject)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey);

        if (roles != null && !roles.isBlank()) {
            builder.claim("roles", roles);
        }

        return builder.compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
