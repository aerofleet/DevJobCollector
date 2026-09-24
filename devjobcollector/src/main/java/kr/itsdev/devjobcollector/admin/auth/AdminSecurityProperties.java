package kr.itsdev.devjobcollector.admin.auth;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "admin.security")
public class AdminSecurityProperties {
    private String sessionCookieName = "DJC_ADMIN_SESSION";
    private String csrfCookieName = "DJC_ADMIN_CSRF";
    private String csrfHeaderName = "X-CSRF-TOKEN";
    private String cookieDomain = ".itsdev.kr";
    private boolean secureCookies = true;
    private Duration sessionDuration = Duration.ofHours(8);
    private int maximumFailedAttempts = 5;
    private Duration lockDuration = Duration.ofMinutes(30);
    private String mfaEncryptionKey = "";
    private List<String> allowedOrigins = new ArrayList<>(List.of(
            "https://djc-admin.itsdev.kr", "http://localhost:5174"));

    public String getSessionCookieName() { return sessionCookieName; }
    public void setSessionCookieName(String value) { this.sessionCookieName = value; }
    public String getCsrfCookieName() { return csrfCookieName; }
    public void setCsrfCookieName(String value) { this.csrfCookieName = value; }
    public String getCsrfHeaderName() { return csrfHeaderName; }
    public void setCsrfHeaderName(String value) { this.csrfHeaderName = value; }
    public String getCookieDomain() { return cookieDomain; }
    public void setCookieDomain(String value) { this.cookieDomain = value; }
    public boolean isSecureCookies() { return secureCookies; }
    public void setSecureCookies(boolean value) { this.secureCookies = value; }
    public Duration getSessionDuration() { return sessionDuration; }
    public void setSessionDuration(Duration value) { this.sessionDuration = value; }
    public int getMaximumFailedAttempts() { return maximumFailedAttempts; }
    public void setMaximumFailedAttempts(int value) { this.maximumFailedAttempts = value; }
    public Duration getLockDuration() { return lockDuration; }
    public void setLockDuration(Duration value) { this.lockDuration = value; }
    public String getMfaEncryptionKey() { return mfaEncryptionKey; }
    public void setMfaEncryptionKey(String value) { this.mfaEncryptionKey = value; }
    public List<String> getAllowedOrigins() { return List.copyOf(allowedOrigins); }
    public void setAllowedOrigins(List<String> value) { this.allowedOrigins = new ArrayList<>(value); }
}
