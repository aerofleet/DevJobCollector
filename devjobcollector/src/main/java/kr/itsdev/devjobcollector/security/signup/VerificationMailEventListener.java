package kr.itsdev.devjobcollector.security.signup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class VerificationMailEventListener {
    private static final Logger log = LoggerFactory.getLogger(VerificationMailEventListener.class);

    private final VerificationMailService mailService;

    public VerificationMailEventListener(VerificationMailService mailService) {
        this.mailService = mailService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendVerificationCode(VerificationCodeIssuedEvent event) {
        try {
            mailService.sendCode(event.email(), event.code());
        } catch (RuntimeException error) {
            log.error("Verification mail delivery failed after transaction commit", error);
        }
    }
}
