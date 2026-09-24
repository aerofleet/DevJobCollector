package kr.itsdev.devjobcollector.admin;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminSessionRepository extends JpaRepository<AdminSession, String> {
    Optional<AdminSession> findByRefreshHash(String refreshHash);
    long deleteByExpiresAtBefore(LocalDateTime threshold);
}
