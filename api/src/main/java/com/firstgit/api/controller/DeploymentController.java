package com.firstgit.api.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.firstgit.api.config.OAuth2TokenStore;
import com.firstgit.api.dto.DeployResponse;
import com.firstgit.api.service.ZipProcessingService;

/**
 * Deployment controller — SINGLE source of truth for all deployment operations.
 * 
 * - Uses JWT cookie authentication (never Bearer token header)
 * - Retrieves the GitHub access token from the in-memory OAuth2TokenStore
 * - All responses use standardized DTOs
 * - All errors go through GlobalExceptionHandler (opaque, no stack traces)
 */
@RestController
@RequestMapping("/api/v1")
public class DeploymentController {

    private static final Logger log = LoggerFactory.getLogger(DeploymentController.class);
    private static final boolean DEBUG_LOGS = "true".equalsIgnoreCase(System.getenv("DEBUG_LOGS"));

    private final ZipProcessingService zipProcessingService;
    private final OAuth2TokenStore tokenStore;

    public DeploymentController(ZipProcessingService zipProcessingService, OAuth2TokenStore tokenStore) {
        this.zipProcessingService = zipProcessingService;
        this.tokenStore = tokenStore;
    }

    /**
     * Deploy a ZIP file as a new GitHub repository.
     * Requires authentication via JWT cookie.
     */
    @PostMapping("/deploy")
    public ResponseEntity<DeployResponse> deployProject(
            @RequestParam("file") MultipartFile file,
            @RequestParam("repoName") String repoName,
            @RequestParam(value = "isPrivate", defaultValue = "false") boolean isPrivate,
            Authentication authentication) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(new DeployResponse("error", null));
        }

        if (authentication == null) {
            return ResponseEntity.status(401).body(new DeployResponse("error", null));
        }

        String githubLogin = authentication.getName();
        String githubToken = tokenStore.getToken(githubLogin);

        if (githubToken == null) {
            log.warn("User {} has no stored GitHub token", githubLogin);
            return ResponseEntity.status(401).body(new DeployResponse("error", null));
        }

        if (DEBUG_LOGS) {
            log.debug("Deploy start for user: {}", githubLogin);
        }

        try {
            String repoUrl = zipProcessingService.processAndDeploy(
                    file,
                    repoName,
                    isPrivate,
                    githubToken);

            log.info("User {} deployed repository: {}", githubLogin, repoName);
            return ResponseEntity.ok(new DeployResponse("success", repoUrl));
        } catch (Exception e) {
            log.error("Deployment failed for user {}: {}", githubLogin, e.getMessage());
            throw new RuntimeException("Deployment failed", e);
        }
    }

    /**
     * Fetch the user's existing GitHub repositories.
     * Used by the frontend to check for name conflicts.
     */
    @GetMapping("/repos")
    public ResponseEntity<List<String>> getExistingRepositories(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }
        String githubLogin = authentication.getName();
        String githubToken = tokenStore.getToken(githubLogin);

        if (githubToken == null) {
            return ResponseEntity.status(401).build();
        }

        try {
            List<String> repoNames = zipProcessingService.fetchUserRepositories(githubToken);
            return ResponseEntity.ok(repoNames);
        } catch (Exception e) {
            log.error("Failed to fetch repos for user {}: {}", githubLogin, e.getMessage());
            return ResponseEntity.status(502).build();
        }
    }

    // Debug logging disabled for production — use Spring logging instead
    // To enable debug logs in development, set DEBUG_LOGS=true environment variable
}
