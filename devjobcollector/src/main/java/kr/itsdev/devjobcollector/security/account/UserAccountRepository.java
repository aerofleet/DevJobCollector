package kr.itsdev.devjobcollector.security.account;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmailIgnoreCase(String email);
    Optional<UserAccount> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
    boolean existsByEmailIgnoreCase(String email);
}
