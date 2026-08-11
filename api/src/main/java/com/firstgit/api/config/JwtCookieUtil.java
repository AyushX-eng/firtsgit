package com.firstgit.api.config;

import java.util.Arrays;
import java.util.Optional;

import org.springframework.http.ResponseCookie;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Utility for creating and reading JWTs stored in HttpOnly cookies.
 *
 * ⚠️ CRITICAL: The `Secure` flag is CONDITIONAL.
 * - In production (HTTPS): Secure=true, SameSite=Strict
 * - In dev (HTTP localhost): Secure=false, SameSite=Lax
 * 
 * If Secure=true on localhost HTTP, the browser SILENTLY DROPS the cookie
 * and the JWT is never sent back — causing "access denied" on every request.
 */
public final class JwtCookieUtil {

    public static final String COOKIE_NAME = "firstgit_jwt";
    private static final String COOKIE_PATH = "/";
    private static final int MAX_AGE_SECONDS = 15 * 60;

    /** Global flag — set once at startup based on environment. */
    private static boolean SECURE_MODE = false;

    private JwtCookieUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Call once at startup from a @PostConstruct or @Bean to set the secure mode.
     * @param isHttps true if the app runs behind HTTPS (production), false for HTTP dev
     */
    public static void setSecureMode(boolean isHttps) {
        SECURE_MODE = isHttps;
    }

    /**
     * Creates the JWT cookie.
     * In dev: HttpOnly + SameSite=Lax (no Secure). The browser will send this
     *         cookie on same-origin requests through the Vite proxy. ✅
     * In prod: HttpOnly + Secure + SameSite=Strict (maximum security). ✅
     */
    public static ResponseCookie createJwtCookie(String jwtToken) {
        return ResponseCookie.from(COOKIE_NAME, jwtToken)
                .httpOnly(true)
                .secure(SECURE_MODE)
                .sameSite(SECURE_MODE ? "Strict" : "Lax")
                .path(COOKIE_PATH)
                .maxAge(MAX_AGE_SECONDS)
                .build();
    }

    /**
     * Creates a logout cookie (maxAge=0 to delete).
     */
    public static ResponseCookie createLogoutCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(SECURE_MODE)
                .sameSite(SECURE_MODE ? "Strict" : "Lax")
                .path(COOKIE_PATH)
                .maxAge(0)
                .build();
    }

    /**
     * Extract JWT from cookie in the incoming request.
     */
    public static Optional<String> extractJwtFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(c -> COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }
}
