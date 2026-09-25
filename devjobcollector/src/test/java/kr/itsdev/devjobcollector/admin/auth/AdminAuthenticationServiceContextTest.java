package kr.itsdev.devjobcollector.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import kr.itsdev.devjobcollector.admin.AdminAccountRepository;
import kr.itsdev.devjobcollector.admin.AdminAuditLogRepository;
import kr.itsdev.devjobcollector.admin.AdminSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminAuthenticationServiceContextTest {
    @Test
    void createsServiceBeanWithProductionConstructor() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.registerBean(AdminAccountRepository.class,
                    () -> mock(AdminAccountRepository.class));
            context.registerBean(AdminSessionRepository.class,
                    () -> mock(AdminSessionRepository.class));
            context.registerBean(AdminAuditLogRepository.class,
                    () -> mock(AdminAuditLogRepository.class));
            context.registerBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class));
            context.registerBean(AdminMfaSecretCipher.class,
                    () -> mock(AdminMfaSecretCipher.class));
            context.registerBean(AdminTotpVerifier.class, () -> mock(AdminTotpVerifier.class));
            context.registerBean(AdminTokenCodec.class, () -> mock(AdminTokenCodec.class));
            context.registerBean(AdminSecurityProperties.class, AdminSecurityProperties::new);
            context.registerBean(AdminAuthenticationService.class);

            context.refresh();

            assertThat(context.getBean(AdminAuthenticationService.class)).isNotNull();
        }
    }
}
