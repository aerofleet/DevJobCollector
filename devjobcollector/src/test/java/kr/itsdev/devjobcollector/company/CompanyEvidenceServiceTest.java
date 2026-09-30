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
        byte[] pdf = "%PDF-1.4\nfixture\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
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

    @Test
    void rejectsImageWithOnlyAValidSignature() {
        byte[] signature = new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        MockMultipartFile file = new MockMultipartFile("file", "registration.png", "image/png", signature);
        assertThatThrownBy(() -> new CompanyEvidenceService(verificationService, jdbcTemplate)
                .submit("1", 20L, file)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsMismatchedExtensionEvenWhenPdfSignatureIsValid() {
        MockMultipartFile file = new MockMultipartFile("file", "registration.jpg", "image/jpeg",
                "%PDF-1.4\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertThatThrownBy(() -> new CompanyEvidenceService(verificationService, jdbcTemplate)
                .submit("1", 20L, file)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void normalizesUploadedImageBeforeStoringIt() throws Exception {
        var image = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", output);
        byte[] original = java.util.Arrays.copyOf(output.toByteArray(), output.size() + 12);
        java.util.Arrays.fill(original, output.size(), original.length, (byte) 'X');
        MockMultipartFile file = new MockMultipartFile("file", "registration.png", "image/png", original);
        when(verificationService.submit(eq("1"), eq(20L), any())).thenReturn(
                new CompanyVerificationResponse(10L, 20L, CompanyVerificationStatus.PENDING,
                        CompanyStatus.PENDING_VERIFICATION, java.time.LocalDateTime.now(), null));

        new CompanyEvidenceService(verificationService, jdbcTemplate).submit("1", 20L, file);

        ArgumentCaptor<byte[]> stored = ArgumentCaptor.forClass(byte[].class);
        verify(jdbcTemplate).update(any(String.class), eq(10L), eq("image/png"), stored.capture());
        assertThat(stored.getValue().length).isLessThan(original.length);
        assertThat(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(stored.getValue()))).isNotNull();
    }
}
