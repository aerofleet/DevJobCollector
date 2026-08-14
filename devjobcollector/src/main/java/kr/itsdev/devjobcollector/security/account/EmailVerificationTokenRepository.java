package kr.itsdev.devjobcollector.security.account;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    Optional<EmailVerificationToken> findFirstByUserAndUsedAtIsNullOrderByIdDesc(UserAccount user);
}
