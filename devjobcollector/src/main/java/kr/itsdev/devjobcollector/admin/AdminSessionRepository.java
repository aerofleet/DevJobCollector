package kr.itsdev.devjobcollector.admin;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface AdminSessionRepository extends JpaRepository<AdminSession, String> {
    @EntityGraph(attributePaths = {"admin", "admin.permissions"})
    Optional<AdminSession> findByRefreshHash(String refreshHash);
    long deleteByExpiresAtBefore(LocalDateTime threshold);
}
