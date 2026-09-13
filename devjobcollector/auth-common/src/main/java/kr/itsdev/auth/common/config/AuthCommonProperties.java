package kr.itsdev.auth.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.common")
public class AuthCommonProperties {
    private String frontendSuccessUri = "http://localhost:5173/oauth/callback";
    private String frontendFailureUri = "http://localhost:5173/oauth/callback";
    private String tokenQueryParam = "token";
    private Duration oauthStateTtl = Duration.ofMinutes(5);
    private int oauthStateMaxPending = 8;

    public String getFrontendSuccessUri() {
        return frontendSuccessUri;
    }

    public void setFrontendSuccessUri(String frontendSuccessUri) {
        this.frontendSuccessUri = frontendSuccessUri;
    }

    public String getFrontendFailureUri() {
        return frontendFailureUri;
    }

    public void setFrontendFailureUri(String frontendFailureUri) {
        this.frontendFailureUri = frontendFailureUri;
    }

    public String getTokenQueryParam() {
        return tokenQueryParam;
    }

    public void setTokenQueryParam(String tokenQueryParam) {
        this.tokenQueryParam = tokenQueryParam;
    }

    public Duration getOauthStateTtl() {
        return oauthStateTtl;
    }

    public void setOauthStateTtl(Duration oauthStateTtl) {
        this.oauthStateTtl = oauthStateTtl;
    }

    public int getOauthStateMaxPending() {
        return oauthStateMaxPending;
    }

    public void setOauthStateMaxPending(int oauthStateMaxPending) {
        this.oauthStateMaxPending = oauthStateMaxPending;
    }
}
