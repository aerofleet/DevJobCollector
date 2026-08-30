package kr.itsdev.devjobcollector.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

class GoogleOAuthConfigurationTest {

    @Test
    void googleLoginUsesConfiguredOAuth2UserServicePath() throws IOException {
        var sources = new YamlPropertySourceLoader()
                .load("application-prod", new ClassPathResource("application-prod.yml"));

        List<String> scopes = sources.stream()
                .flatMap(source -> List.of(0, 1, 2).stream()
                        .map(index -> source.getProperty(
                                "spring.security.oauth2.client.registration.google.scope[" + index + "]")))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();

        assertThat(scopes).containsExactly("profile", "email");
        assertThat(scopes).doesNotContain("openid");
    }
}
