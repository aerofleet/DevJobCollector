package kr.itsdev.devjobcollector.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ResumeTextExtractor {
    static final int MAX_BYTES = 5 * 1024 * 1024;
    private final Path workerJar;
    private final Duration timeout;
    private final Semaphore capacity = new Semaphore(2);

    @org.springframework.beans.factory.annotation.Autowired
    public ResumeTextExtractor(@Value("${ai.preprocessing.worker-jar:}") String workerJar) {
        this(workerJar, Duration.ofSeconds(12));
    }

    ResumeTextExtractor(String workerJar, Duration timeout) {
        this.workerJar = workerJar.isBlank() ? null : Path.of(workerJar).toAbsolutePath();
        this.timeout = timeout;
    }

    public String extract(MultipartFile file) {
        if (workerJar == null || !java.nio.file.Files.isRegularFile(workerJar)) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, "EXTRACTOR_UNAVAILABLE");
        }
        if (!capacity.tryAcquire()) throw failure(HttpStatus.TOO_MANY_REQUESTS, "EXTRACTOR_BUSY");
        try {
            String mime = validate(file);
            byte[] bytes;
            try (var input = file.getInputStream()) {
                bytes = input.readNBytes(MAX_BYTES + 1);
            }
            if (bytes.length == 0 || bytes.length > MAX_BYTES || !hasMagic(bytes, mime)) {
                throw failure(HttpStatus.BAD_REQUEST, "INVALID_DOCUMENT");
            }
            return run(bytes, mime);
        } catch (IOException exception) {
            throw failure(HttpStatus.BAD_REQUEST, "DOCUMENT_READ_FAILED");
        } finally {
            capacity.release();
        }
    }

    private String run(byte[] bytes, String mime) {
        String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java";
        Process process;
        try {
            var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                    "-Xmx192m", "-XX:+ExitOnOutOfMemoryError", "-Djava.awt.headless=true",
                    "-jar", workerJar.toString(), mime).redirectError(ProcessBuilder.Redirect.DISCARD);
            // Do not expose provider keys, DB passwords or inherited JVM injection options to the parser.
            builder.environment().keySet().removeIf(key -> !java.util.Set.of(
                    "PATH", "SYSTEMROOT", "WINDIR", "TEMP", "TMP", "LANG").contains(key.toUpperCase(Locale.ROOT)));
            process = builder.start();
        } catch (IOException exception) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, "EXTRACTOR_UNAVAILABLE");
        }
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var writer = executor.submit(() -> {
                try (var stream = process.getOutputStream()) { stream.write(bytes); }
                return null;
            });
            var reader = executor.submit(() -> process.getInputStream().readNBytes(240_001));
            try {
                if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw failure(HttpStatus.UNPROCESSABLE_ENTITY, "EXTRACTION_TIMEOUT");
                }
                writer.get(1, TimeUnit.SECONDS);
                byte[] result = reader.get(1, TimeUnit.SECONDS);
                if (process.exitValue() != 0 || result.length > 240_000) {
                    throw failure(HttpStatus.UNPROCESSABLE_ENTITY, "EXTRACTION_FAILED");
                }
                String text = new String(result, StandardCharsets.UTF_8);
                if (text.isBlank() || text.length() > 60_000) {
                    throw failure(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_EXTRACTED_TEXT");
                }
                return text;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw failure(HttpStatus.SERVICE_UNAVAILABLE, "EXTRACTION_INTERRUPTED");
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exception) {
                throw failure(HttpStatus.UNPROCESSABLE_ENTITY, "EXTRACTION_FAILED");
            } finally {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                try {
                    process.getInputStream().close();
                    process.getOutputStream().close();
                } catch (IOException ignored) { /* No document content is logged. */ }
            }
        }
    }

    static String validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw failure(HttpStatus.BAD_REQUEST, "INVALID_DOCUMENT_SIZE");
        }
        String filename = file.getOriginalFilename(), mime = file.getContentType();
        if (filename == null || filename.length() > 255 || filename.codePoints().anyMatch(Character::isISOControl)) {
            throw failure(HttpStatus.BAD_REQUEST, "INVALID_DOCUMENT_NAME");
        }
        String name = filename.toLowerCase(Locale.ROOT);
        if (("application/pdf".equals(mime) && name.endsWith(".pdf"))
                || ("image/png".equals(mime) && name.endsWith(".png"))
                || ("image/jpeg".equals(mime) && (name.endsWith(".jpg") || name.endsWith(".jpeg")))) return mime;
        throw failure(HttpStatus.BAD_REQUEST, "INVALID_DOCUMENT_TYPE");
    }

    static boolean hasMagic(byte[] bytes, String mime) {
        byte[] magic = switch (mime) {
            case "application/pdf" -> "%PDF-".getBytes(StandardCharsets.US_ASCII);
            case "image/png" -> new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
            case "image/jpeg" -> new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff};
            default -> new byte[0];
        };
        return magic.length > 0 && bytes.length >= magic.length
                && java.util.Arrays.equals(magic, java.util.Arrays.copyOf(bytes, magic.length));
    }

    static ResponseStatusException failure(HttpStatus status, String code) {
        return new ResponseStatusException(status, code);
    }
}
