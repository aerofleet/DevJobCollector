package kr.itsdev.devjobcollector.jobfile;

import io.micrometer.core.instrument.MeterRegistry;
import io.netty.channel.ChannelOption;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

@RestController
final class JobFilePreviewController {
    static final long MAX_BYTES = 30L * 1024 * 1024;
    private static final byte[] HWP_MAGIC = {(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0, (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1};
    private final JdbcTemplate jdbc;
    private final MeterRegistry meters;
    private final WebClient web;
    private final java.util.concurrent.Semaphore concurrentStreams = new java.util.concurrent.Semaphore(3);

    @org.springframework.beans.factory.annotation.Autowired
    JobFilePreviewController(JdbcTemplate jdbc, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.meters = meters;
        var client = HttpClient.create().resolver(new PublicAddressResolverGroup())
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .responseTimeout(Duration.ofSeconds(15)).followRedirect(false);
        this.web = WebClient.builder().clientConnector(new ReactorClientHttpConnector(client)).build();
    }

    JobFilePreviewController(JdbcTemplate jdbc, MeterRegistry meters, WebClient web) {
        this.jdbc = jdbc;
        this.meters = meters;
        this.web = web;
    }

    @GetMapping("/api/v1/job-files/{fileId}/preview")
    ResponseEntity<StreamingResponseBody> preview(@PathVariable long fileId) {
        var rows = jdbc.query("SELECT f.file_name, f.file_url FROM job_files f JOIN job_posts p ON p.id=f.job_post_id WHERE f.id=? AND p.is_active=1 AND p.moderation_status='ACTIVE' AND p.end_date>=?",
                (rs, index) -> new Source(rs.getString(1), rs.getString(2)), fileId, LocalDate.now(ZoneId.of("Asia/Seoul")));
        if (rows.size() != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var source = rows.getFirst();
        final URI uri;
        try { uri = PreviewSourcePolicy.source(source.name(), source.url()); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
        if (!concurrentStreams.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "PREVIEW_BUSY");
        try {
        meters.counter("job_file_preview_open").increment();
        StreamingResponseBody body = output -> {
            long started = System.nanoTime();
            try {
                long written = stream(fetch(uri, source.name(), 0), source.name(), output);
                meters.counter("job_file_preview_success").increment();
                meters.summary("job_file_preview_download_bytes").record(written);
            } catch (Exception ex) {
                meters.counter("job_file_preview_error").increment();
                throw new IOException("Preview unavailable", ex);
            } finally {
                concurrentStreams.release();
                meters.timer("job_file_preview_latency").record(Duration.ofNanos(System.nanoTime() - started));
            }
        };
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(source.name(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .header("X-Content-Type-Options", "nosniff").body(body);
        } catch (RuntimeException ex) {
            concurrentStreams.release();
            throw ex;
        }
    }

    Flux<DataBuffer> fetch(URI uri, String filename, int redirects) {
        return web.get().uri(uri).header(HttpHeaders.ACCEPT_ENCODING, "identity").exchangeToFlux(response -> {
            int status = response.statusCode().value();
            if (status >= 300 && status < 400) {
                var location = response.headers().asHttpHeaders().getLocation();
                if (redirects >= 3 || location == null) return response.releaseBody().thenMany(Flux.error(new IOException("Invalid redirect")));
                try {
                    var next = PreviewSourcePolicy.source(filename, uri.resolve(location).toString());
                    return response.releaseBody().thenMany(fetch(next, filename, redirects + 1));
                } catch (IllegalArgumentException ex) { return response.releaseBody().thenMany(Flux.error(new IOException("Blocked redirect"))); }
            }
            long size = response.headers().contentLength().orElse(-1);
            if (status != 200 || size > MAX_BYTES) return response.releaseBody().thenMany(Flux.error(new IOException("Source unavailable")));
            return response.bodyToFlux(DataBuffer.class);
        }).timeout(Duration.ofSeconds(30)).doOnDiscard(DataBuffer.class, DataBufferUtils::release);
    }

    static long stream(Flux<? extends DataBuffer> input, String filename, java.io.OutputStream output) throws IOException {
        long written = 0;
        byte[] prefix = new byte[filename.toLowerCase(java.util.Locale.ROOT).endsWith(".webp") ? 12 : 8];
        int prefixLength = 0;
        try (var buffers = input.toStream(1)) {
            var iterator = buffers.iterator();
            while (iterator.hasNext()) {
                DataBuffer buffer = iterator.next();
                try {
                    int remaining = buffer.readableByteCount();
                    if (written + remaining > MAX_BYTES) throw new IOException("File size limit");
                    while (remaining > 0) {
                        int size = Math.min(remaining, 8192);
                        byte[] chunk = new byte[size];
                        buffer.read(chunk);
                        int consumed = 0;
                        while (prefixLength < prefix.length && consumed < size) prefix[prefixLength++] = chunk[consumed++];
                        if (written < prefix.length && prefixLength == prefix.length) {
                            if (!validMagic(filename, prefix)) throw new IOException("Unsupported file format");
                            output.write(prefix);
                        }
                        if (prefixLength == prefix.length) output.write(chunk, consumed, size - consumed);
                        written += size;
                        remaining -= size;
                    }
                } finally { DataBufferUtils.release(buffer); }
            }
            if (prefixLength < prefix.length) throw new IOException("Incomplete file");
            return written;
        }
    }

    static boolean validMagic(String filename, byte[] prefix) {
        String name = filename.toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".hwpx"))
            return prefix.length >= 4 && prefix[0] == 0x50 && prefix[1] == 0x4b && prefix[2] == 0x03 && prefix[3] == 0x04;
        if (name.endsWith(".hwp")) return Arrays.equals(prefix, HWP_MAGIC);
        if (name.endsWith(".pdf")) return prefix.length >= 5 && prefix[0] == '%' && prefix[1] == 'P' && prefix[2] == 'D' && prefix[3] == 'F' && prefix[4] == '-';
        if (name.endsWith(".png")) return Arrays.equals(prefix, new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return prefix.length >= 3 && prefix[0] == (byte) 0xff && prefix[1] == (byte) 0xd8 && prefix[2] == (byte) 0xff;
        if (name.endsWith(".webp")) return prefix.length >= 12 && prefix[0] == 'R' && prefix[1] == 'I' && prefix[2] == 'F' && prefix[3] == 'F' && prefix[8] == 'W' && prefix[9] == 'E' && prefix[10] == 'B' && prefix[11] == 'P';
        return false;
    }

    private record Source(String name, String url) {}

    @PostMapping("/api/v1/job-files/preview-events")
    ResponseEntity<Void> event(@RequestParam String event) {
        String metric = switch (event) {
            case "render_success" -> "job_file_preview_render_success";
            case "parse_error" -> "job_file_preview_parse_error";
            case "fallback" -> "job_file_preview_fallback_original";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        };
        meters.counter(metric).increment();
        return ResponseEntity.noContent().build();
    }
}
