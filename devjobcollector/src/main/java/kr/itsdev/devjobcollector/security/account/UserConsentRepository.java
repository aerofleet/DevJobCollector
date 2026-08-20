package kr.itsdev.devjobcollector.security.account;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {
    List<UserConsent> findAllByUserAndConsentTypeOrderByOccurredAtAscIdAsc(
            UserAccount user, ConsentType consentType);

    Optional<UserConsent> findFirstByUserAndConsentTypeAndPolicyVersionOrderByOccurredAtDescIdDesc(
            UserAccount user, ConsentType consentType, String policyVersion);
}
