package com.firstgit.api.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Zero-trust JWT service.
 * Issues short-lived tokens (default 15 min) signed with HMAC-SHA256.
 * No persistent storage — stateless by design.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs) {

        boolean secureCookies = "true".equalsIgnoreCase(System.getenv("SECURE_COOKIES"));
        boolean defaultOrWeakSecret = secret == null || secret.isBlank()
                || "CHANGE_ME_IN_PRODUCTION_TO_A_64BYTE_SECRET".equals(secret);

        if (defaultOrWeakSecret) {
            if (secureCookies) {
                throw new IllegalStateException("JWT_SECRET must be set to a strong value when SECURE_COOKIES=true");
            }
            log.warn("⚠️ JWT secret is using a default/weak value. Set JWT_SECRET environment variable in production.");
        }
        // Must be at least 256 bits (32 bytes) for HS256
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            if (secureCookies) {
                throw new IllegalStateException("JWT_SECRET must be at least 32 bytes when SECURE_COOKIES=true");
            }
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
            keyBytes = padded;
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    /**
     * Creates a signed JWT for the given GitHub OAuth2 user.
     * Claims: subject = GitHub login, issuer = firstgit, issued-at, expiration.
     */
    public String createToken(String githubLogin, String githubAvatarUrl, String githubName) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(githubLogin)
                .issuer("firstgit")
                .issuedAt(now)
                .expiration(expiration)
                .claim("avatar", githubAvatarUrl != null ? githubAvatarUrl : "")
                .claim("name", githubName != null ? githubName : githubLogin)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validates and parses a JWT. Returns null if invalid/expired.
     * Never throws — always returns null on failure.
     */
    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer("firstgit")
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("JWT expired: {}", e.getMessage());
        } catch (SecurityException | MalformedJwtException e) {
            log.warn("JWT signature/integrity validation failed: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT format: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty: {}", e.getMessage());
        }
        return null;
    }
}
