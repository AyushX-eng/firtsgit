package com.firstgit.api;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;

import com.firstgit.api.config.OAuth2AuthenticationSuccessHandler;

@SpringBootApplication
public class ApiApplication {

    private static final Logger log = LoggerFactory.getLogger(ApiApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }

    @Bean
    ApplicationListener<ApplicationReadyEvent> preflightChecks() {
        return event -> {
            boolean secure = "true".equalsIgnoreCase(System.getenv("SECURE_COOKIES"));

            String frontendUrl = System.getenv("FRONTEND_URL");
            String clientId = System.getenv("GITHUB_CLIENT_ID");
            String clientSecret = System.getenv("GITHUB_CLIENT_SECRET");
            String jwtSecret = System.getenv("JWT_SECRET");

            if (secure) {
                boolean failed = false;
                StringBuilder problems = new StringBuilder();

                if (frontendUrl == null || frontendUrl.isBlank()
                        || frontendUrl.startsWith("http://localhost")
                        || frontendUrl.startsWith("http://127.0.0.1")) {
                    problems.append("\n  - FRONTEND_URL is missing or still points to localhost. ")
                            .append("Set FRONTEND_URL to your production frontend origin (e.g. https://your-site.netlify.app)");
                    failed = true;
                }

                if (clientId == null || clientId.isBlank()
                        || clientId.contains("${") || clientId.startsWith("CHANGE_ME")) {
                    problems.append("\n  - GITHUB_CLIENT_ID is not set. Configure it as an environment variable in your hosting dashboard.");
                    failed = true;
                }

                if (clientSecret == null || clientSecret.isBlank()
                        || clientSecret.contains("${") || clientSecret.startsWith("CHANGE_ME")) {
                    problems.append("\n  - GITHUB_CLIENT_SECRET is not set. Configure it as an environment variable in your hosting dashboard.");
                    failed = true;
                }

                if (jwtSecret == null || jwtSecret.isBlank()
                        || jwtSecret.startsWith("CHANGE_ME") || jwtSecret.length() < 32) {
                    problems.append("\n  - JWT_SECRET is weak or missing. Set a strong 64+ character secret via environment variable.");
                    failed = true;
                }

                if (failed) {
                    String msg = "🛑 Refusing to start in SECURE_COOKIES=true (production) mode because of misconfiguration:"
                            + problems;
                    log.error(msg);
                    SpringApplication.exit(event.getApplicationContext(), () -> 78);
                    throw new IllegalStateException(msg);
                }

                List<String> allowedOrigins = parseAllowedOrigins(System.getenv("CORS_ALLOWED_ORIGINS"));
                if (!allowedOrigins.isEmpty() && frontendUrl != null && !allowedOrigins.contains(frontendUrl)) {
                    log.warn("⚠️ CORS_ALLOWED_ORIGINS does not include FRONTEND_URL ({}). Update CORS_ALLOWED_ORIGINS to include it, or expect cross-origin failures.", frontendUrl);
                }
            }

            String redirectBase = frontendUrl != null && !frontendUrl.isBlank()
                    ? frontendUrl
                    : OAuth2AuthenticationSuccessHandler.DEFAULT_FRONTEND_URL;
            log.info("✅ OAuth success redirect base: {}", redirectBase);
        };
    }

    public static List<String> parseAllowedOrigins(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
