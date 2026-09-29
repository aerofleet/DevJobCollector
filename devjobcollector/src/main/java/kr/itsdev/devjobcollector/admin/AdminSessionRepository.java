package kr.itsdev.devjobcollector.admin;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminSessionRepository extends JpaRepository<AdminSession, String> {
    @EntityGraph(attributePaths = {"admin", "admin.permissions"})
    Optional<AdminSession> findByRefreshHash(String refreshHash);
    long deleteByExpiresAtBefore(LocalDateTime threshold);

    @Modifying
    @Query("UPDATE AdminSession s SET s.revokedAt = :when "
            + "WHERE s.admin.id = :adminId AND s.revokedAt IS NULL AND s.expiresAt > :when")
    int revokeActiveByAdminId(@Param("adminId") Long adminId,
                              @Param("when") LocalDateTime when);
}
