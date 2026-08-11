package com.firstgit.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ephemeral in-memory store for GitHub OAuth2 access tokens.
 * Keyed by GitHub login (username).
 * 
 * No database is used intentionally — tokens live only in memory.
 * On server restart, all sessions must re-authenticate (zero-trust).
 * 
 * ⚠️ This is not suitable for multi-instance deployments without a sticky session
 *    or shared cache. For production scale, replace with Redis.
 */
@Component
public class OAuth2TokenStore {

    private static final Logger log = LoggerFactory.getLogger(OAuth2TokenStore.class);

    private final ConcurrentHashMap<String, String> tokenMap = new ConcurrentHashMap<>();

    /**
     * Store a GitHub access token for a user.
     */
    public void store(String githubLogin, String accessToken) {
        if (githubLogin == null || accessToken == null) {
            log.warn("Attempted to store null login/token");
            return;
        }
        tokenMap.put(githubLogin, accessToken);
        log.debug("Stored token for user: {}", githubLogin);
    }

    /**
     * Retrieve a GitHub access token for a user.
     */
    public String getToken(String githubLogin) {
        if (githubLogin == null) {
            return null;
        }
        return tokenMap.get(githubLogin);
    }

    /**
     * Remove a stored token (e.g., on logout).
     */
    public void remove(String githubLogin) {
        if (githubLogin != null) {
            tokenMap.remove(githubLogin);
            log.debug("Removed token for user: {}", githubLogin);
        }
    }

    /**
     * Check if a user has a stored token.
     */
    public boolean hasToken(String githubLogin) {
        return githubLogin != null && tokenMap.containsKey(githubLogin);
    }

    /**
     * Current number of stored sessions (for monitoring).
     */
    public int getActiveSessionCount() {
        return tokenMap.size();
    }
}

