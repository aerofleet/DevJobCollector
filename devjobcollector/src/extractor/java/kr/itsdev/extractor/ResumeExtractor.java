package kr.itsdev.extractor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Untrusted documents run in a disposable, heap-limited JVM. No Spring, network clients,
 * filenames or document metadata are emitted. The caller enforces a wall-clock deadline.
 */
public final class ResumeExtractor {
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_CHARS = 60_000;

    public static void main(String[] args) {
        try {
            if (args.length != 1) throw new IllegalArgumentException();
            byte[] bytes = System.in.readNBytes(MAX_BYTES + 1);
            if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException();
            String text = switch (args[0]) {
                case "application/pdf" -> pdf(bytes);
                case "image/png", "image/jpeg" -> image(bytes, args[0]);
                default -> throw new IllegalArgumentException();
            };
            if (text.isBlank() || text.length() > MAX_CHARS) throw new IllegalArgumentException();
            System.out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception | OutOfMemoryError error) {
            // Parser exceptions can contain user content: never print their messages or traces.
            System.exit(2);
        }
    }

    private static String pdf(byte[] bytes) throws Exception {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted() || document.getNumberOfPages() < 1
                    || document.getNumberOfPages() > 10
                    || !document.getCurrentAccessPermission().canExtractContent()) {
                throw new IllegalArgumentException();
            }
            var output = new BoundedText();
            var stripper = new PDFTextStripper();
            var renderer = new PDFRenderer(document);
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                stripper.setStartPage(page + 1);
                stripper.setEndPage(page + 1);
                var pageText = new BoundedText();
                stripper.writeText(document, pageText);
                String text = pageText.toString();
                if (text.isBlank()) {
                    var box = document.getPage(page).getCropBox();
                    if (box.getWidth() <= 0 || box.getHeight() <= 0
                            || box.getWidth() > 1500 || box.getHeight() > 1500) {
                        throw new IllegalArgumentException();
                    }
                    text = ocr(renderer.renderImageWithDPI(page, 120));
                }
                if (page > 0) output.write("\f");
                output.write(text.replace('\f', ' ' ) + "\n");
            }
            return output.toString();
        }
    }

    private static String image(byte[] bytes, String mime) throws Exception {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException();
            var reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!(mime.equals("image/png") && format.equalsIgnoreCase("png"))
                        && !(mime.equals("image/jpeg") && format.equalsIgnoreCase("jpeg"))) {
                    throw new IllegalArgumentException();
                }
                reader.setInput(input);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 10_000_000L) {
                    throw new IllegalArgumentException();
                }
                return ocr(reader.read(0)).replace('\f', ' ');
            } finally {
                reader.dispose();
            }
        }
    }

    private static String ocr(java.awt.image.BufferedImage image) throws Exception {
        // Re-encoding removes image metadata before the local OCR process receives pixels.
        var encoded = new ByteArrayOutputStream();
        ImageIO.write(image, "png", encoded);
        var process = new ProcessBuilder("tesseract", "stdin", "stdout", "-l", "kor+eng", "--dpi", "120")
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var input = executor.submit(() -> {
                try (OutputStream stream = process.getOutputStream()) { stream.write(encoded.toByteArray()); }
                return null;
            });
            var output = executor.submit(() -> process.getInputStream().readNBytes(MAX_CHARS * 4 + 1));
            try {
                if (!process.waitFor(8, TimeUnit.SECONDS)) throw new IllegalArgumentException();
                input.get(1, TimeUnit.SECONDS);
                byte[] result = output.get(1, TimeUnit.SECONDS);
                if (process.exitValue() != 0 || result.length > MAX_CHARS * 4) throw new IllegalArgumentException();
                return new String(result, StandardCharsets.UTF_8);
            } finally {
                process.destroyForcibly();
                process.getInputStream().close();
                process.getOutputStream().close();
            }
        }
    }

    private static final class BoundedText extends Writer {
        private final StringBuilder text = new StringBuilder();
        @Override public void write(char[] chars, int offset, int length) {
            if (text.length() + length > MAX_CHARS) throw new IllegalArgumentException();
            text.append(chars, offset, length);
        }
        @Override public void flush() {}
        @Override public void close() {}
        @Override public String toString() { return text.toString(); }
    }
}
