package com.firstgit.api.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Zero-trust JWT authentication filter.
 * Extracts JWT from HttpOnly cookie, validates it, and sets the SecurityContext.
 * Runs on every request — no session is trusted by default.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Skip filter for OAuth redirect paths — authentication is in progress
        String path = request.getRequestURI();
        if (path.startsWith("/oauth2/") || path.startsWith("/login/oauth2/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract JWT from HttpOnly cookie (never from Authorization header or URL)
        Optional<String> jwtOpt = JwtCookieUtil.extractJwtFromCookie(request);

        if (jwtOpt.isEmpty()) {
            log.debug("No JWT cookie found for request: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = jwtOpt.get();

        // Validate the JWT — null returned if invalid/expired
        Claims claims = jwtService.validateToken(jwt);
        if (claims == null) {
            log.debug("Invalid or expired JWT for request: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        String githubLogin = claims.getSubject();
        String avatarUrl = claims.get("avatar", String.class);
        String name = claims.get("name", String.class);

        if (githubLogin == null || githubLogin.isBlank()) {
            log.warn("JWT with missing subject (login) claim");
            filterChain.doFilter(request, response);
            return;
        }

        log.debug("Authenticated user: {} via JWT cookie", githubLogin);

        // Build authentication object
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        githubLogin,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                );

        // Attach both WebAuthenticationDetails AND user metadata
        // We store user metadata in an attribute of the request instead of overwriting details
        WebAuthenticationDetails webDetails = new WebAuthenticationDetailsSource().buildDetails(request);
        authentication.setDetails(webDetails);

        // Store user metadata in request attribute (avoids overwriting WebAuthenticationDetails)
        request.setAttribute("jwtUserDetails", new JwtUserDetails(githubLogin, avatarUrl, name, jwt));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    /**
     * Simple DTO for JWT-authenticated user details stored in the SecurityContext.
     */
    public record JwtUserDetails(String githubLogin, String avatarUrl, String name, String tokenValue) {}
}
