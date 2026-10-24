package kr.itsdev.devjobcollector.admin;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminIdempotencyKeyRepository extends JpaRepository<AdminIdempotencyKey, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT key FROM AdminIdempotencyKey key "
            + "WHERE key.adminId = :adminId AND key.key = :idempotencyKey")
    Optional<AdminIdempotencyKey> findForUpdate(@Param("adminId") Long adminId,
                                                @Param("idempotencyKey") String idempotencyKey);
}
