package kr.itsdev.auth.common.model;

public enum SocialProvider {
    GOOGLE,
    GITHUB;

    public static SocialProvider fromRegistrationId(String registrationId) {
        if (registrationId == null) {
            throw new IllegalArgumentException("registrationId must not be null");
        }

        return switch (registrationId.toLowerCase()) {
            case "google" -> GOOGLE;
            case "github" -> GITHUB;
            default -> throw new IllegalArgumentException("Unsupported provider: " + registrationId);
        };
    }
}
