package kr.itsdev.auth.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.common")
public class AuthCommonProperties {
    private String frontendSuccessUri = "http://localhost:5173/oauth/callback";
    private String tokenQueryParam = "token";

    public String getFrontendSuccessUri() {
        return frontendSuccessUri;
    }

    public void setFrontendSuccessUri(String frontendSuccessUri) {
        this.frontendSuccessUri = frontendSuccessUri;
    }

    public String getTokenQueryParam() {
        return tokenQueryParam;
    }

    public void setTokenQueryParam(String tokenQueryParam) {
        this.tokenQueryParam = tokenQueryParam;
    }
}
