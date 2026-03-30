package kr.itsdev.devjobcollector.security.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import kr.itsdev.auth.common.model.AuthenticatedUser;
import kr.itsdev.auth.common.model.SocialProfile;
import kr.itsdev.auth.common.spi.SocialUserUpsertService;
import kr.itsdev.devjobcollector.security.model.SocialUserRecord;
import org.springframework.stereotype.Service;

@Service
public class InMemorySocialUserUpsertService implements SocialUserUpsertService {
    private final AtomicLong sequence = new AtomicLong(1L);
    private final Map<String, SocialUserRecord> byProviderKey = new ConcurrentHashMap<>();
    private final Map<String, SocialUserRecord> byEmail = new ConcurrentHashMap<>();

    @Override
    public AuthenticatedUser upsert(SocialProfile profile) {
        String providerKey = profile.provider() + ":" + profile.providerUserId();
        SocialUserRecord existing = byProviderKey.get(providerKey);

        if (existing != null) {
            SocialUserRecord updated = new SocialUserRecord(
                    existing.id(),
                    existing.provider(),
                    existing.providerUserId(),
                    normalizeEmail(profile.email(), existing.email()),
                    normalizeName(profile.name(), existing.name()),
                    existing.role()
            );
            byProviderKey.put(providerKey, updated);
            if (updated.email() != null) {
                byEmail.put(updated.email(), updated);
            }
            return new AuthenticatedUser(updated.id(), updated.email(), updated.name(), updated.role());
        }

        SocialUserRecord byMail = profile.email() == null ? null : byEmail.get(profile.email());
        if (byMail != null) {
            SocialUserRecord linked = new SocialUserRecord(
                    byMail.id(),
                    profile.provider(),
                    profile.providerUserId(),
                    byMail.email(),
                    normalizeName(profile.name(), byMail.name()),
                    byMail.role()
            );
            byProviderKey.put(providerKey, linked);
            byEmail.put(linked.email(), linked);
            return new AuthenticatedUser(linked.id(), linked.email(), linked.name(), linked.role());
        }

        long id = sequence.getAndIncrement();
        String email = normalizeEmail(profile.email(), "social-" + id + "@local");
        String name = normalizeName(profile.name(), "user-" + id);
        SocialUserRecord created = new SocialUserRecord(
                id,
                profile.provider(),
                profile.providerUserId(),
                email,
                name,
                "USER"
        );
        byProviderKey.put(providerKey, created);
        byEmail.put(email, created);
        return new AuthenticatedUser(created.id(), created.email(), created.name(), created.role());
    }

    private String normalizeEmail(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toLowerCase();
    }

    private String normalizeName(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
