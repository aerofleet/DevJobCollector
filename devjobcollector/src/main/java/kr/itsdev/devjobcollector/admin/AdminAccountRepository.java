package kr.itsdev.devjobcollector.admin;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminAccountRepository extends JpaRepository<AdminAccount, Long> {
    Optional<AdminAccount> findByEmail(String email);
    boolean existsByEmail(String email);

    @Query("SELECT a FROM AdminAccount a WHERE (:keyword IS NULL OR "
            + "LOWER(a.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
            + "LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            + "AND (:role IS NULL OR a.role = :role) "
            + "AND (:status IS NULL OR a.status = :status)")
    Page<AdminAccount> searchForAdmin(@Param("keyword") String keyword,
            @Param("role") AdminRole role, @Param("status") AdminAccountStatus status,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AdminAccount a WHERE a.id = :id")
    Optional<AdminAccount> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from AdminAccount account where account.email = :email")
    Optional<AdminAccount> findByEmailForUpdate(@Param("email") String email);
}
