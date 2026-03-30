package kr.itsdev.devjobcollector.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.token")
public class AuthTokenProperties {
    private String issuer = "devjobcollector";
    private String secret = "change-this-secret-at-least-32chars";
    private long expiresMinutes = 120;

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpiresMinutes() {
        return expiresMinutes;
    }

    public void setExpiresMinutes(long expiresMinutes) {
        this.expiresMinutes = expiresMinutes;
    }
}
