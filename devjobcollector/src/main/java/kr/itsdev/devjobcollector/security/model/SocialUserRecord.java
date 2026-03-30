package kr.itsdev.devjobcollector.security.model;

import kr.itsdev.auth.common.model.SocialProvider;

public record SocialUserRecord(
        Long id,
        SocialProvider provider,
        String providerUserId,
        String email,
        String name,
        String role
) {
}
