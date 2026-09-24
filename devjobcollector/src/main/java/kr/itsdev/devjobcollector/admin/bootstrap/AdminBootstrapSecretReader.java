package kr.itsdev.devjobcollector.admin.bootstrap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapSecretReader {
    public char[] read(AdminBootstrapProperties properties) {
        boolean hasDirectSecret = properties.getPassword() != null
                && !properties.getPassword().isEmpty();
        boolean hasSecretFile = properties.getPasswordFile() != null
                && !properties.getPasswordFile().isBlank();
        if (hasDirectSecret == hasSecretFile) {
            throw new IllegalStateException(
                    "exactly one of admin.bootstrap.password or password-file is required");
        }
        if (hasDirectSecret) {
            return properties.getPassword().toCharArray();
        }

        Path secretPath = Path.of(properties.getPasswordFile()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(secretPath) || !Files.isReadable(secretPath)) {
            throw new IllegalStateException("admin bootstrap password file is not readable");
        }
        try {
            return stripSingleLineEnding(Files.readString(secretPath, StandardCharsets.UTF_8))
                    .toCharArray();
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read admin bootstrap password file", exception);
        }
    }

    private String stripSingleLineEnding(String value) {
        if (value.endsWith("\r\n")) {
            return value.substring(0, value.length() - 2);
        }
        if (value.endsWith("\n")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
