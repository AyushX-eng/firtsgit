package com.firstgit.api.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.firstgit.api.config.JwtAuthenticationFilter;
import com.firstgit.api.config.JwtCookieUtil;
import com.firstgit.api.config.JwtService;
import com.firstgit.api.config.OAuth2AuthenticationSuccessHandler;
import com.firstgit.api.config.OAuth2TokenStore;
import com.firstgit.api.dto.AuthResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authentication controller.
 * 
 * Uses TOKEN EXCHANGE pattern to resolve the SameSite cookie cross-origin issue.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final boolean DEBUG_LOGS = "true".equalsIgnoreCase(System.getenv("DEBUG_LOGS"));

    private final OAuth2TokenStore tokenStore;
    private final OAuth2AuthenticationSuccessHandler successHandler;
    private final JwtService jwtService;

    public AuthController(OAuth2TokenStore tokenStore, 
                          OAuth2AuthenticationSuccessHandler successHandler,
                          JwtService jwtService) {
        this.tokenStore = tokenStore;
        this.successHandler = successHandler;
        this.jwtService = jwtService;
    }

    /**
     * Exchange an auth code (from OAuth callback redirect) for a JWT cookie.
     */
    @PostMapping("/exchange")
    public ResponseEntity<Map<String, Object>> exchangeAuthCode(
            @RequestParam("code") String authCode,
            HttpServletResponse response) {

        OAuth2AuthenticationSuccessHandler.AuthCodeUserDetails user = successHandler.exchangeAuthCode(authCode);

        if (user == null || user.githubLogin() == null) {
            log.warn("Invalid or expired auth code used");
            return ResponseEntity.status(401).body(Map.of(
                "error", "Invalid or expired auth code. Please log in again."
            ));
        }

        // Create JWT and set as HttpOnly cookie (set for the frontend's origin via proxy)
        String jwt = jwtService.createToken(user.githubLogin(), user.avatarUrl(), user.name());
        ResponseCookie jwtCookie = JwtCookieUtil.createJwtCookie(jwt);
        response.addHeader("Set-Cookie", jwtCookie.toString());

        log.info("Auth code exchanged successfully for user: {}", user.githubLogin());
        if (DEBUG_LOGS) {
            log.debug("JWT cookie set for user: {}", user.githubLogin());
        }
        return ResponseEntity.ok(Map.of(
            "status", "authenticated",
            "username", user.githubLogin()
        ));
    }

    /**
     * Check authentication status via JWT cookie.
     */
    @GetMapping("/status")
    public ResponseEntity<AuthResponse> getAuthStatus(HttpServletRequest request, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof String githubLogin)) {
            return ResponseEntity.ok(new AuthResponse(false, null, null, null));
        }

        if (!tokenStore.hasToken(githubLogin)) {
            log.warn("User {} has JWT but no stored GitHub token", githubLogin);
            return ResponseEntity.ok(new AuthResponse(false, null, null, null));
        }

        String avatarUrl = null;
        String name = null;

        Object detailsAttr = request.getAttribute("jwtUserDetails");
        if (detailsAttr instanceof JwtAuthenticationFilter.JwtUserDetails jwtUser) {
            avatarUrl = jwtUser.avatarUrl();
            name = jwtUser.name();
        }

        if (name == null || name.isBlank()) {
            name = githubLogin;
        }

        return ResponseEntity.ok(new AuthResponse(true, githubLogin, avatarUrl, name));
    }
}
