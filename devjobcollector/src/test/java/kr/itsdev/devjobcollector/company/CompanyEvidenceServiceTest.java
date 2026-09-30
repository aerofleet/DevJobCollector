package kr.itsdev.devjobcollector.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import kr.itsdev.devjobcollector.dto.company.CompanyVerificationResponse;
import kr.itsdev.devjobcollector.dto.company.CompanyVerificationSubmitRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CompanyEvidenceServiceTest {
    @Mock CompanyVerificationService verificationService;
    @Mock JdbcTemplate jdbcTemplate;

    @Test
    void submitsPdfWithServerGeneratedKeyAndStoresItsBytes() {
        byte[] pdf = "%PDF-1.4\nfixture".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile("file", "registration.pdf", "application/pdf", pdf);
        CompanyVerificationResponse response = new CompanyVerificationResponse(
                10L, 20L, CompanyVerificationStatus.PENDING,
                CompanyStatus.PENDING_VERIFICATION, java.time.LocalDateTime.now(), null);
        when(verificationService.submit(eq("1"), eq(20L), any())).thenReturn(response);

        CompanyVerificationResponse result = new CompanyEvidenceService(verificationService, jdbcTemplate)
                .submit("1", 20L, file);

        assertThat(result).isEqualTo(response);
        ArgumentCaptor<CompanyVerificationSubmitRequest> request =
                ArgumentCaptor.forClass(CompanyVerificationSubmitRequest.class);
        verify(verificationService).submit(eq("1"), eq(20L), request.capture());
        assertThat(request.getValue().evidenceObjectKey()).startsWith("company-verification/");
        verify(jdbcTemplate).update(any(String.class), eq(10L), eq("application/pdf"), eq(pdf));
    }

    @Test
    void rejectsFilesWithoutSupportedMagicBytes() {
        MockMultipartFile file = new MockMultipartFile("file", "registration.pdf", "application/pdf",
                "not a pdf".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertThatThrownBy(() -> new CompanyEvidenceService(verificationService, jdbcTemplate)
                .submit("1", 20L, file)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }
}
