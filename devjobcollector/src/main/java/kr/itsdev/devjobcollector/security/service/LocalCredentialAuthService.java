package kr.itsdev.devjobcollector.security.service;

import java.util.Locale;
import kr.itsdev.auth.common.model.AuthenticatedUser;
import kr.itsdev.devjobcollector.security.AuthLocalLoginProperties;
import org.springframework.stereotype.Service;

@Service
public class LocalCredentialAuthService {
    private final AuthLocalLoginProperties properties;

    public LocalCredentialAuthService(AuthLocalLoginProperties properties) {
        this.properties = properties;
    }

    public AuthenticatedUser authenticate(String identifier, String password) {
        if (!properties.isEnabled() || identifier == null || password == null) {
            return null;
        }

        String normalizedIdentifier = identifier.trim().toLowerCase(Locale.ROOT);
        for (AuthLocalLoginProperties.User user : properties.getUsers()) {
            if (!matchesIdentifier(user, normalizedIdentifier)) {
                continue;
            }
            if (user.getPassword() == null || !user.getPassword().equals(password)) {
                return null;
            }
            Long id = user.getId() == null ? 0L : user.getId();
            String name = user.getName() == null || user.getName().isBlank() ? user.getUsername() : user.getName();
            String role = user.getRole() == null || user.getRole().isBlank() ? "USER" : user.getRole();
            return new AuthenticatedUser(id, user.getEmail(), name, role);
        }
        return null;
    }

    private boolean matchesIdentifier(AuthLocalLoginProperties.User user, String normalizedIdentifier) {
        String username = user.getUsername() == null ? "" : user.getUsername().trim().toLowerCase(Locale.ROOT);
        String email = user.getEmail() == null ? "" : user.getEmail().trim().toLowerCase(Locale.ROOT);
        return normalizedIdentifier.equals(username) || normalizedIdentifier.equals(email);
    }
}
