package kr.itsdev.devjobcollector.admin.bootstrap;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "admin.bootstrap", name = "enabled", havingValue = "true")
public class AdminBootstrapRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AdminBootstrapProperties properties;
    private final AdminBootstrapSecretReader secretReader;
    private final AdminBootstrapService bootstrapService;

    public AdminBootstrapRunner(AdminBootstrapProperties properties,
                                AdminBootstrapSecretReader secretReader,
                                AdminBootstrapService bootstrapService) {
        this.properties = properties;
        this.secretReader = secretReader;
        this.bootstrapService = bootstrapService;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (properties.getMfaSecret() == null || properties.getMfaSecret().isBlank()) {
            throw new IllegalStateException(
                    "ADMIN_BOOTSTRAP_MFA_SECRET is required when admin bootstrap is enabled");
        }
        char[] password = secretReader.read(properties);
        try {
            AdminBootstrapResult result = bootstrapService.provision(
                    properties.getEmail(), properties.getName(), password,
                    properties.getMfaSecret());
            if (result == AdminBootstrapResult.CREATED) {
                log.info("Initial SUPER_ADMIN provisioning completed");
            } else {
                log.info("Initial SUPER_ADMIN already exists; provisioning skipped");
            }
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
