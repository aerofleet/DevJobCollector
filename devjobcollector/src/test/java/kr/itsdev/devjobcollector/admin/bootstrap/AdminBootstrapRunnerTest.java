package kr.itsdev.devjobcollector.admin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
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
        char[] password = "long-bootstrap-passphrase".toCharArray();
        AdminBootstrapSecretReader secretReader = mock(AdminBootstrapSecretReader.class);
        AdminBootstrapService bootstrapService = mock(AdminBootstrapService.class);
        when(secretReader.read(properties)).thenReturn(password);
        when(bootstrapService.provision(properties.getEmail(), properties.getName(), password))
                .thenReturn(AdminBootstrapResult.CREATED);
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                properties, secretReader, bootstrapService);

        runner.run(mock(ApplicationArguments.class));

        verify(bootstrapService).provision(properties.getEmail(), properties.getName(), password);
        assertThat(password).containsOnly('\0');
    }
}
