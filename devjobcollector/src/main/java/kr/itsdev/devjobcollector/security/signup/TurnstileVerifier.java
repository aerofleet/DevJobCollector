package kr.itsdev.devjobcollector.security.signup;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Component
public class TurnstileVerifier {
    private static final String SITEVERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
    private final AuthSignupProperties properties;
    private final RestClient restClient = RestClient.create();

    public TurnstileVerifier(AuthSignupProperties properties) {
        this.properties = properties;
    }

    public void verify(String token, String remoteIp) {
        if (!properties.isTurnstileEnabled()) {
            return;
        }
        if (token == null || token.isBlank() || properties.getTurnstileSecret().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "봇 방지 인증이 필요합니다.");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", properties.getTurnstileSecret());
        form.add("response", token);
        if (remoteIp != null && !remoteIp.isBlank()) {
            form.add("remoteip", remoteIp);
        }

        Map<?, ?> result;
        try {
            result = restClient.post()
                    .uri(SITEVERIFY_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "봇 방지 인증 서버에 연결할 수 없습니다.", ex);
        }
        if (result == null || !Boolean.TRUE.equals(result.get("success"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "봇 방지 인증에 실패했습니다.");
        }
    }
}
