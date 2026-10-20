package kr.itsdev.devjobcollector.ai;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;

class ResumeTextExtractorTest {
    private final String jar = System.getProperty("ai.extractor.test-jar");
    private final ResumeTextExtractor extractor = new ResumeTextExtractor(jar);

    @Test
    void extractsRealPdfInSeparateJvmWithoutEmittingMetadata() throws Exception {
        try (var pdf = document(1)) {
            pdf.getDocumentInformation().setAuthor("Private Author");
            var output = new ByteArrayOutputStream();
            pdf.save(output);
            String text = extractor.extract(file("resume.pdf", "application/pdf", output.toByteArray()));
            assertThat(text).contains("Java Spring 1200ms").doesNotContain("Private Author");
        }
    }

    @Test
    void rejectsMimeExtensionAndMagicMismatches() {
        for (var file : java.util.List.of(
                file("x.pdf", "image/png", "%PDF-1.4".getBytes()),
                file("x.exe", "application/pdf", "%PDF-1.4".getBytes()),
                file("x.pdf", "application/pdf", "hello".getBytes()),
                file("x.png", "image/png", "%PDF-1.4".getBytes()),
                file("x.pdf", "application/pdf", new byte[0]),
                file("x.pdf", "application/pdf", new byte[ResumeTextExtractor.MAX_BYTES + 1]))) {
            assertThatThrownBy(() -> extractor.extract(file)).hasMessageContaining("400");
        }
    }

    @Test
    void rejectsTruncatedPdfAndExcessivePages() throws Exception {
        assertThatThrownBy(() -> extractor.extract(file("x.pdf", "application/pdf", "%PDF-1.4\n%%EOF".getBytes())))
                .hasMessageContaining("EXTRACTION_FAILED");
        try (var pdf = document(11)) {
            var output = new ByteArrayOutputStream();
            pdf.save(output);
            assertThatThrownBy(() -> extractor.extract(file("x.pdf", "application/pdf", output.toByteArray())))
                    .hasMessageContaining("EXTRACTION_FAILED");
        }
    }

    @Test
    void enforcesDeadlineAndFailsClosedWithoutWorker() throws Exception {
        try (var pdf = document(1)) {
            var output = new ByteArrayOutputStream();
            pdf.save(output);
            var upload = file("x.pdf", "application/pdf", output.toByteArray());
            assertThatThrownBy(() -> new ResumeTextExtractor(jar, Duration.ZERO).extract(upload))
                    .hasMessageContaining("EXTRACTION_TIMEOUT");
            assertThatThrownBy(() -> new ResumeTextExtractor("").extract(upload))
                    .hasMessageContaining("EXTRACTOR_UNAVAILABLE");
        }
    }

    @Test
    void rejectsEncryptedPdf() throws Exception {
        try (var pdf = document(1)) {
            pdf.protect(new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy(
                    "owner", "reader", new org.apache.pdfbox.pdmodel.encryption.AccessPermission()));
            var output = new ByteArrayOutputStream();
            pdf.save(output);
            assertThatThrownBy(() -> extractor.extract(file("x.pdf", "application/pdf", output.toByteArray())))
                    .hasMessageContaining("EXTRACTION_FAILED");
        }
    }

    @Test
    void rejectsImageDecompressionBombBeforeReadingPixels() throws Exception {
        var image = new java.awt.image.BufferedImage(4000, 3000, java.awt.image.BufferedImage.TYPE_BYTE_BINARY);
        var output = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", output);
        assertThat(output.size()).isLessThan(ResumeTextExtractor.MAX_BYTES);
        assertThatThrownBy(() -> extractor.extract(file("x.png", "image/png", output.toByteArray())))
                .hasMessageContaining("EXTRACTION_FAILED");
    }

    private PDDocument document(int count) throws Exception {
        var document = new PDDocument();
        for (int i = 0; i < count; i++) {
            var page = new PDPage();
            document.addPage(page);
            try (var stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(40, 700);
                stream.showText("Java Spring 1200ms");
                stream.endText();
            }
        }
        return document;
    }

    private MockMultipartFile file(String name, String mime, byte[] bytes) {
        return new MockMultipartFile("file", name, mime, bytes);
    }
}
