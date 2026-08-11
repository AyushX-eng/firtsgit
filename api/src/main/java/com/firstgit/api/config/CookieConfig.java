package com.firstgit.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Configures JwtCookieUtil at startup based on the running environment.
 * 
 * On Render/Netlify (HTTPS): cookies get Secure flag.
 * On localhost (HTTP): cookies do NOT get Secure flag, otherwise browser drops them.
 */
@Component
public class CookieConfig {

    private static final Logger log = LoggerFactory.getLogger(CookieConfig.class);

    private final boolean secureCookies;

    public CookieConfig(@Value("${SECURE_COOKIES:false}") boolean secureCookies) {
        this.secureCookies = secureCookies;
    }

    @PostConstruct
    public void init() {
        JwtCookieUtil.setSecureMode(secureCookies);
        if (secureCookies) {
            log.info("🔒 JWT cookies configured with Secure flag (HTTPS mode)");
        } else {
            log.info("🔓 JWT cookies configured WITHOUT Secure flag (HTTP dev mode)");
        }
    }
}
