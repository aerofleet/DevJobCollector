package kr.itsdev.auth.common.model;

import java.util.Locale;

public enum SocialProvider {
    GOOGLE,
    GITHUB,
    KAKAO,
    NAVER,
    APPLE;

    public static SocialProvider fromRegistrationId(String registrationId) {
        if (registrationId == null) {
            throw new IllegalArgumentException("registrationId must not be null");
        }

        return switch (registrationId.toLowerCase(Locale.ROOT)) {
            case "google" -> GOOGLE;
            case "github" -> GITHUB;
            case "kakao" -> KAKAO;
            case "naver" -> NAVER;
            case "apple" -> APPLE;
            default -> throw new IllegalArgumentException("Unsupported provider: " + registrationId);
        };
    }
}
