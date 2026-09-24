package kr.itsdev.devjobcollector.admin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

class AdminBootstrapRunnerTest {
    @Test
    void clearsPasswordBufferAfterProvisioning() {
        AdminBootstrapProperties properties = new AdminBootstrapProperties();
        properties.setEmail("admin@example.com");
        properties.setName("관리자");
        properties.setMfaSecret("JBSWY3DPEHPK3PXP");
        char[] password = "long-bootstrap-passphrase".toCharArray();
        AdminBootstrapSecretReader secretReader = mock(AdminBootstrapSecretReader.class);
        AdminBootstrapService bootstrapService = mock(AdminBootstrapService.class);
        when(secretReader.read(properties)).thenReturn(password);
        when(bootstrapService.provision(properties.getEmail(), properties.getName(), password,
                properties.getMfaSecret()))
                .thenReturn(AdminBootstrapResult.CREATED);
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                properties, secretReader, bootstrapService);

        runner.run(mock(ApplicationArguments.class));

        verify(bootstrapService).provision(properties.getEmail(), properties.getName(), password,
                properties.getMfaSecret());
        assertThat(password).containsOnly('\0');
    }

    @Test
    void refusesBootstrapWithoutMfaSecret() {
        AdminBootstrapProperties properties = new AdminBootstrapProperties();
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                properties, mock(AdminBootstrapSecretReader.class),
                mock(AdminBootstrapService.class));

        assertThatThrownBy(() -> runner.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_BOOTSTRAP_MFA_SECRET");
    }
}
