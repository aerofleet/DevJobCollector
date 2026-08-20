package kr.itsdev.devjobcollector.security.account;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {
    Optional<UserIdentity> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject);
    Optional<UserIdentity> findByUserAndProvider(UserAccount user, AuthProvider provider);
    List<UserIdentity> findAllByUserOrderByIdAsc(UserAccount user);
    boolean existsByUserAndProvider(UserAccount user, AuthProvider provider);
}
