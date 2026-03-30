package kr.itsdev.auth.common.model;

public record SocialProfile(
        SocialProvider provider,
        String providerUserId,
        String email,
        String name,
        String profileImageUrl
) {
}
