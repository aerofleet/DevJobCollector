package kr.itsdev.devjobcollector.admin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdminBootstrapSecretReaderTest {
    private final AdminBootstrapSecretReader reader = new AdminBootstrapSecretReader();

    @Test
    void readsDirectSecretWithoutTrimming() {
        AdminBootstrapProperties properties = new AdminBootstrapProperties();
        properties.setPassword(" passphrase with spaces ");

        assertThat(reader.read(properties)).containsExactly(
                " passphrase with spaces ".toCharArray());
    }

    @Test
    void readsFileAndRemovesOnlyOneTrailingLineEnding(@TempDir Path tempDirectory)
            throws Exception {
        Path secretFile = tempDirectory.resolve("admin-password");
        Files.writeString(secretFile, "file-passphrase  \r\n");
        AdminBootstrapProperties properties = new AdminBootstrapProperties();
        properties.setPasswordFile(secretFile.toString());

        assertThat(reader.read(properties)).containsExactly("file-passphrase  ".toCharArray());
    }

    @Test
    void rejectsMissingOrAmbiguousSecretSources(@TempDir Path tempDirectory) throws Exception {
        AdminBootstrapProperties missing = new AdminBootstrapProperties();
        assertThatThrownBy(() -> reader.read(missing))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly one");

        Path secretFile = tempDirectory.resolve("admin-password");
        Files.writeString(secretFile, "file-passphrase");
        AdminBootstrapProperties ambiguous = new AdminBootstrapProperties();
        ambiguous.setPassword("direct-passphrase");
        ambiguous.setPasswordFile(secretFile.toString());

        assertThatThrownBy(() -> reader.read(ambiguous))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly one");
    }
}
