package com.firstgit.api.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory rate limiting filter using Bucket4j (token bucket algorithm).
 * 
 * - Authenticated users: 50 requests/minute (keyed by GitHub login)
 * - Anonymous users: 20 requests/minute (keyed by IP address)
 * - Deployment endpoint: 5 requests/minute (special high-cost operation)
 * 
 * No database needed — all state is in-memory.
 * Cleared on server restart (acceptable for zero-trust, forces re-auth).
 */
@Component
public class RateLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final int AUTHENTICATED_CAPACITY = 50;
    private static final int AUTHENTICATED_REFILL_PER_MINUTE = 50;

    private static final int ANONYMOUS_CAPACITY = 20;
    private static final int ANONYMOUS_REFILL_PER_MINUTE = 20;

    private static final int DEPLOY_CAPACITY = 5;
    private static final int DEPLOY_REFILL_PER_MINUTE = 5;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public void init(FilterConfig filterConfig) {
        log.info("RateLimitFilter initialized");
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getRequestURI();
        boolean isDeployEndpoint = path.startsWith("/api/v1/deploy") || path.startsWith("/api/deploy");

        // Determine bucket key: authenticated users by login, anonymous by IP
        String bucketKey = resolveBucketKey(request);

        // Choose bandwidth limits based on auth state and endpoint
        Bucket bucket = buckets.computeIfAbsent(bucketKey, key -> {
            Bandwidth limit;
            if (isDeployEndpoint) {
                // Deployment is expensive — strict limit
                limit = Bandwidth.classic(DEPLOY_CAPACITY,
                        Refill.intervally(DEPLOY_REFILL_PER_MINUTE, Duration.ofMinutes(1)));
            } else if (isAuthenticated(request)) {
                limit = Bandwidth.classic(AUTHENTICATED_CAPACITY,
                        Refill.intervally(AUTHENTICATED_REFILL_PER_MINUTE, Duration.ofMinutes(1)));
            } else {
                limit = Bandwidth.classic(ANONYMOUS_CAPACITY,
                        Refill.intervally(ANONYMOUS_REFILL_PER_MINUTE, Duration.ofMinutes(1)));
            }
            return Bucket.builder().addLimit(limit).build();
        });

        if (bucket.tryConsume(1)) {
            // Add rate limit headers for transparency
            response.setHeader("X-RateLimit-Limit",
                    String.valueOf(isDeployEndpoint ? DEPLOY_CAPACITY :
                            isAuthenticated(request) ? AUTHENTICATED_CAPACITY : ANONYMOUS_CAPACITY));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(bucket.getAvailableTokens()));
            filterChain.doFilter(servletRequest, servletResponse);
        } else {
            log.warn("Rate limit exceeded for key: {}", maskKey(bucketKey));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too many requests. Please slow down.\"}");
        }
    }

    private String resolveBucketKey(HttpServletRequest request) {
        // If authenticated via JWT, use GitHub login
        if (isAuthenticated(request)) {
            return "user:" + request.getUserPrincipal().getName();
        }
        // Otherwise use IP
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        } else {
            // Take first IP in chain
            ip = ip.split(",")[0].trim();
        }
        return "ip:" + ip;
    }

    private boolean isAuthenticated(HttpServletRequest request) {
        return request.getUserPrincipal() != null;
    }

    private String maskKey(String key) {
        if (key.startsWith("ip:")) {
            String ip = key.substring(3);
            int lastDot = ip.lastIndexOf('.');
            if (lastDot > 0) {
                return "ip:" + ip.substring(0, lastDot) + ".xxx";
            }
        }
        return key;
    }

    @Override
    public void destroy() {
        buckets.clear();
        log.info("RateLimitFilter destroyed, all buckets cleared");
    }
}

