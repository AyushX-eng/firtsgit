package com.firstgit.api.dto;

/**
 * Standardized response for authentication status.
 * Never includes the actual token value — only metadata.
 */
public class AuthResponse {

    private boolean authenticated;
    private String username;
    private String avatarUrl;
    private String name;

    public AuthResponse() {
    }

    public AuthResponse(boolean authenticated, String username, String avatarUrl, String name) {
        this.authenticated = authenticated;
        this.username = username;
        this.avatarUrl = avatarUrl;
        this.name = name;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

