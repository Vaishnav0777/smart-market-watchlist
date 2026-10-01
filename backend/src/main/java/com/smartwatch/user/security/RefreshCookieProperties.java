package com.smartwatch.user.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Flags for the refresh-token cookie. The cookie is always HttpOnly.
 * {@code secure} should be true when the site is served over HTTPS.
 */
@ConfigurationProperties(prefix = "app.security.cookie")
public class RefreshCookieProperties {

    private boolean secure;

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }
}
