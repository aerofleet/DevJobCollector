package kr.itsdev.devjobcollector.security.account;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    @Query("""
            select u from UserAccount u
            where (:keyword is null or lower(u.email) like lower(concat('%', :keyword, '%'))
                or lower(u.name) like lower(concat('%', :keyword, '%')))
              and (:status is null or u.status = :status)
            """)
    Page<UserAccount> searchForAdmin(@Param("keyword") String keyword,
            @Param("status") UserAccountStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.id = :id")
    Optional<UserAccount> findByIdForModeration(@Param("id") Long id);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            LocalDateTime fromInclusive, LocalDateTime toExclusive);

    @Query(value = """
            SELECT DATE(created_at) AS signupDay, COUNT(*) AS total
            FROM users
            WHERE created_at >= :fromInclusive AND created_at < :toExclusive
            GROUP BY DATE(created_at)
            ORDER BY signupDay
            """, nativeQuery = true)
    List<DailySignupCount> countSignupsByDay(
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive);

    interface DailySignupCount {
        LocalDate getSignupDay();
        long getTotal();
    }
}
