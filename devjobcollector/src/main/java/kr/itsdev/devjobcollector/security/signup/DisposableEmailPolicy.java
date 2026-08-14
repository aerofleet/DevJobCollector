package kr.itsdev.devjobcollector.security.signup;

import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class DisposableEmailPolicy {
    private static final Set<String> BLOCKED_DOMAINS = Set.of(
            "10minutemail.com", "guerrillamail.com", "mailinator.com", "temp-mail.org", "yopmail.com"
    );

    public void validate(String email) {
        int separator = email.lastIndexOf('@');
        String domain = separator < 0 ? "" : email.substring(separator + 1).toLowerCase();
        if (BLOCKED_DOMAINS.contains(domain)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "일회용 이메일은 사용할 수 없습니다.");
        }
    }
}
