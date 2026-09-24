package kr.itsdev.devjobcollector.admin.auth;

import java.util.Set;
import kr.itsdev.devjobcollector.admin.AdminPermission;
import kr.itsdev.devjobcollector.admin.AdminRole;

public record AdminPrincipal(
        Long id,
        String email,
        String name,
        AdminRole role,
        Set<AdminPermission> permissions,
        String sessionId,
        String csrfHash
) {
    public AdminPrincipal {
        permissions = Set.copyOf(permissions);
    }
}
