package kr.itsdev.devjobcollector.security.signup;

import java.time.LocalDateTime;
import java.util.List;
import kr.itsdev.devjobcollector.security.account.ConsentType;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.account.UserConsent;
import kr.itsdev.devjobcollector.security.account.UserConsentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RequiredConsentService {
    private final UserConsentRepository consentRepository;
    private final AuthSignupProperties properties;

    public RequiredConsentService(
            UserConsentRepository consentRepository,
            AuthSignupProperties properties
    ) {
        this.consentRepository = consentRepository;
        this.properties = properties;
    }

    public void validateAccepted(boolean termsAccepted, boolean privacyAccepted) {
        if (!termsAccepted || !privacyAccepted) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CONSENT_REQUIRED");
        }
    }

    public void recordAccepted(UserAccount user) {
        LocalDateTime acceptedAt = LocalDateTime.now();
        consentRepository.saveAll(List.of(
                UserConsent.accepted(
                        user,
                        ConsentType.TERMS_OF_SERVICE,
                        properties.getTermsPolicyVersion(),
                        acceptedAt
                ),
                UserConsent.accepted(
                        user,
                        ConsentType.PRIVACY_POLICY,
                        properties.getPrivacyPolicyVersion(),
                        acceptedAt
                )
        ));
    }
}
