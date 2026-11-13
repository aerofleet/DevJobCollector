package kr.itsdev.devjobcollector.admin.companies;

import java.util.Set;
import kr.itsdev.devjobcollector.admin.AdminAuditLogRepository;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.collection.discovery.SmeJobCompanySeedImporter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AdminPublicSeedImportServiceTest {
    @Test
    void reviewerCannotImport() {
        var importer = mock(SmeJobCompanySeedImporter.class);
        var audit = mock(AdminAuditLogRepository.class);
        var service = new AdminPublicSeedImportService(importer, audit);
        var actor = new AdminPrincipal(1L, "r@example.com", "Reviewer", AdminRole.REVIEWER,
                Set.of(), "session", "csrf");
        assertThatThrownBy(() -> service.importFile(new MockMultipartFile("file", new byte[]{1}),
                "UTF-8", actor, "request", null, null)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        verifyNoInteractions(importer, audit);
    }
}
