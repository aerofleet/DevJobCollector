package kr.itsdev.devjobcollector.security.signup;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class SignupRateLimiter {
    private static final Duration WINDOW = Duration.ofHours(1);
    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private final AuthSignupProperties properties;

    public SignupRateLimiter(AuthSignupProperties properties) {
        this.properties = properties;
    }

    public void check(String remoteIp, String email) {
        checkKey("ip:" + normalize(remoteIp));
        checkKey("email:" + normalize(email));
    }

    private void checkKey(String key) {
        Instant now = Instant.now();
        Deque<Instant> timestamps = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            Instant cutoff = now.minus(WINDOW);
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.removeFirst();
            }
            if (timestamps.size() >= properties.getRequestsPerHour()) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "회원가입 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
            }
            timestamps.addLast(now);
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim().toLowerCase();
    }
}
