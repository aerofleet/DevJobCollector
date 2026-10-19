package kr.itsdev.devjobcollector.career;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

class ResumeAttachmentServiceTest {
    @Test
    void acceptsPdfAndSanitizesFilename() {
        var file = new MockMultipartFile("file", "C:\\fakepath\\포트폴리오.pdf", "application/pdf", "%PDF-1.4\n%%EOF".getBytes());
        var validated = ResumeAttachmentService.validate(file);
        assertThat(validated.filename()).isEqualTo("포트폴리오.pdf");
        assertThat(validated.type()).isEqualTo("application/pdf");
    }

    @Test
    void rejectsEmptyOversizedDisguisedAndIncompleteFiles() {
        for (var file : java.util.List.of(
                new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]),
                new MockMultipartFile("file", "big.pdf", "application/pdf", new byte[ResumeAttachmentService.MAX_BYTES + 1]),
                new MockMultipartFile("file", "fake.pdf", "application/pdf", "<html>fake</html>".getBytes()),
                new MockMultipartFile("file", "broken.pdf", "application/pdf", "%PDF-1.4\ntruncated".getBytes()),
                new MockMultipartFile("file", "script.html", "application/pdf", "%PDF-1.4\n%%EOF".getBytes()))) {
            assertThatThrownBy(() -> ResumeAttachmentService.validate(file)).isInstanceOf(ResponseStatusException.class);
        }
    }

    @Test
    void acceptsRealPngAndRejectsExtensionMismatch() throws Exception {
        var image = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", output);
        assertThat(ResumeAttachmentService.validate(new MockMultipartFile("file", "image.png", "image/png", output.toByteArray())).type())
                .isEqualTo("image/png");
        assertThatThrownBy(() -> ResumeAttachmentService.validate(new MockMultipartFile("file", "image.jpg", "image/jpeg", output.toByteArray())))
                .isInstanceOf(ResponseStatusException.class);
    }
}
