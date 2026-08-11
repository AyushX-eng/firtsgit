package com.firstgit.api.config;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Handles successful GitHub OAuth2 login using a TOKEN EXCHANGE pattern:
 * 
 * 1. After GitHub OAuth succeeds, a short-lived auth code (30 seconds) is generated
 * 2. The user is redirected to the FRONTEND with ?auth_code=<code> in the URL
 * 3. The frontend calls POST /api/auth/exchange with this code (through the Vite proxy,
 *    so it's same-origin — the JWT cookie gets set for the frontend's origin)
 * 4. Backend validates the code, creates the JWT, and sets it as an HttpOnly cookie
 * 
 * This resolves the SameSite cookie issue where the cookie set on port 8080
 * was not accessible from port 5173 (different origins = different cookie jars).
 */
@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long AUTH_CODE_TTL_MS = 30_000; // 30 seconds

    private final JwtService jwtService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final OAuth2TokenStore tokenStore;

    // In-memory store for auth codes: code -> {githubLogin, expiry}
    private final ConcurrentHashMap<String, AuthCodeEntry> authCodes = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();

    // Configurable frontend URL — set FRONTEND_URL env var for production
    private static final String FRONTEND_URL = System.getenv("FRONTEND_URL") != null 
            ? System.getenv("FRONTEND_URL") 
            : "http://localhost:5173";

    public OAuth2AuthenticationSuccessHandler(JwtService jwtService,
                                              OAuth2AuthorizedClientService authorizedClientService,
                                              OAuth2TokenStore tokenStore) {
        this.jwtService = jwtService;
        this.authorizedClientService = authorizedClientService;
        this.tokenStore = tokenStore;

        // Clean up expired auth codes every 30 seconds
        cleanupExecutor.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            authCodes.entrySet().removeIf(entry -> entry.getValue().expiryMs() < now);
        }, 30, 30, TimeUnit.SECONDS);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            log.warn("Authentication is not OAuth2: {}", authentication.getClass().getName());
            getRedirectStrategy().sendRedirect(request, response, FRONTEND_URL + "?error=auth_failed");
            return;
        }

        OAuth2User oAuth2User = oauthToken.getPrincipal();
        String githubLogin = oAuth2User.getAttribute("login");
        String avatarUrl = oAuth2User.getAttribute("avatar_url");
        String name = oAuth2User.getAttribute("name");

        if (githubLogin == null) {
            log.error("GitHub login is null — OAuth2 response missing 'login' attribute");
            getRedirectStrategy().sendRedirect(request, response, FRONTEND_URL + "?error=missing_login");
            return;
        }

        // Store the GitHub access token for subsequent API calls
        OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(),
                oauthToken.getName()
        );

        if (authorizedClient != null && authorizedClient.getAccessToken() != null) {
            tokenStore.store(githubLogin, authorizedClient.getAccessToken().getTokenValue());
            log.info("Stored GitHub token for user: {}", githubLogin);
        } else {
            log.warn("No authorized client found for user: {}", githubLogin);
        }

        // Generate a short-lived auth code
        String authCode = generateAuthCode();
        authCodes.put(authCode, new AuthCodeEntry(githubLogin, avatarUrl, name, System.currentTimeMillis() + AUTH_CODE_TTL_MS));

        log.info("OAuth2 login successful for {} - redirecting to frontend with auth code", githubLogin);

        // Redirect to frontend with auth code (NOT the JWT, NOT the GitHub token)
        String targetUrl = UriComponentsBuilder.fromUriString(FRONTEND_URL)
                .queryParam("auth_code", authCode)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    /**
     * Exchange an auth code for a JWT cookie.
     * Called by the frontend as POST /api/auth/exchange (same-origin through proxy).
     * 
     * @param authCode The short-lived auth code from the URL query param
     * @return The user details if valid, null otherwise
     */
    public AuthCodeUserDetails exchangeAuthCode(String authCode) {
        if (authCode == null) return null;

        AuthCodeEntry entry = authCodes.remove(authCode); // Single-use: remove immediately
        if (entry == null) {
            log.warn("Auth code not found or already used: {}", authCode);
            return null;
        }

        if (System.currentTimeMillis() > entry.expiryMs()) {
            log.warn("Auth code expired: {}", authCode);
            return null;
        }

        return new AuthCodeUserDetails(entry.githubLogin(), entry.avatarUrl(), entry.name());
    }

    private String generateAuthCode() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Cleanup the executor on shutdown.
     */
    @PreDestroy
    public void destroy() {
        cleanupExecutor.shutdown();
    }

    private record AuthCodeEntry(String githubLogin, String avatarUrl, String name, long expiryMs) {}

    public record AuthCodeUserDetails(String githubLogin, String avatarUrl, String name) {}
}
