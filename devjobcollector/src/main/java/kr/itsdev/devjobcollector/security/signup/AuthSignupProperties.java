package kr.itsdev.devjobcollector.security.signup;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.signup")
public class AuthSignupProperties {
    private int verificationMinutes = 15;
    private int maxVerificationAttempts = 5;
    private int requestsPerHour = 5;
    private boolean mailDeliveryEnabled;
    private boolean exposeCodeInResponse;
    private String mailFrom = "no-reply@devjobs.local";
    private boolean turnstileEnabled;
    private String turnstileSecret = "";

    public int getVerificationMinutes() { return verificationMinutes; }
    public void setVerificationMinutes(int value) { this.verificationMinutes = value; }
    public int getMaxVerificationAttempts() { return maxVerificationAttempts; }
    public void setMaxVerificationAttempts(int value) { this.maxVerificationAttempts = value; }
    public int getRequestsPerHour() { return requestsPerHour; }
    public void setRequestsPerHour(int value) { this.requestsPerHour = value; }
    public boolean isMailDeliveryEnabled() { return mailDeliveryEnabled; }
    public void setMailDeliveryEnabled(boolean value) { this.mailDeliveryEnabled = value; }
    public boolean isExposeCodeInResponse() { return exposeCodeInResponse; }
    public void setExposeCodeInResponse(boolean value) { this.exposeCodeInResponse = value; }
    public String getMailFrom() { return mailFrom; }
    public void setMailFrom(String value) { this.mailFrom = value; }
    public boolean isTurnstileEnabled() { return turnstileEnabled; }
    public void setTurnstileEnabled(boolean value) { this.turnstileEnabled = value; }
    public String getTurnstileSecret() { return turnstileSecret; }
    public void setTurnstileSecret(String value) { this.turnstileSecret = value; }
}
