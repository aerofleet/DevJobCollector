package kr.itsdev.devjobcollector.jobfile;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JobFilePreviewControllerTest {
    private final DefaultDataBufferFactory buffers = new DefaultDataBufferFactory();
    private final byte[] hwp = {(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0, (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1, 1, 2};

    @Test void doesNotFetchUnknownOrUnavailableFiles() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), anyLong(), any(java.time.LocalDate.class))).thenReturn(java.util.List.of());
        var calls = new AtomicInteger();
        var web = WebClient.builder().exchangeFunction(request -> { calls.incrementAndGet(); return Mono.error(new AssertionError("must not fetch")); }).build();
        var controller = new JobFilePreviewController(jdbc, new SimpleMeterRegistry(), web);
        assertThatThrownBy(() -> controller.preview(999)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        assertThat(calls.get()).isZero();
    }

    @Test void metricsUseOnlyBoundedEventNames() {
        var metrics = new SimpleMeterRegistry();
        var controller = new JobFilePreviewController(null, metrics, WebClient.create());
        assertThat(controller.event("render_success").getStatusCode().value()).isEqualTo(204);
        assertThat(metrics.counter("job_file_preview_render_success").count()).isEqualTo(1);
        assertThatThrownBy(() -> controller.event("untrusted")).isInstanceOf(ResponseStatusException.class);
    }

    @Test void streamsFragmentedSignatureWithoutLosingBytes() throws Exception {
        var output = new ByteArrayOutputStream();
        var input = Flux.just(buffers.wrap(java.util.Arrays.copyOfRange(hwp, 0, 3)), buffers.wrap(java.util.Arrays.copyOfRange(hwp, 3, 10)));
        assertThat(JobFilePreviewController.stream(input, "doc.hwp", output)).isEqualTo(10);
        assertThat(output.toByteArray()).isEqualTo(hwp);
        byte[] large = java.util.Arrays.copyOf(hwp, 20000);
        java.util.Arrays.fill(large, 10, large.length, (byte) 0x51);
        var largerOutput = new ByteArrayOutputStream();
        assertThat(JobFilePreviewController.stream(Flux.just(buffers.wrap(large)), "doc.hwp", largerOutput)).isEqualTo(large.length);
        assertThat(largerOutput.toByteArray()).isEqualTo(large);
    }

    @Test void rejectsHtmlShortFilesAndExcessiveStreams() {
        assertThatThrownBy(() -> JobFilePreviewController.stream(Flux.just(buffers.wrap("<html>not a file".getBytes())), "doc.hwp", new ByteArrayOutputStream())).isInstanceOf(java.io.IOException.class);
        assertThatThrownBy(() -> JobFilePreviewController.stream(Flux.just(buffers.wrap(new byte[2])), "doc.hwpx", new ByteArrayOutputStream())).isInstanceOf(java.io.IOException.class);
        var large = Flux.concat(Flux.just(buffers.wrap(hwp)), Flux.just(buffers.wrap(new byte[(int) JobFilePreviewController.MAX_BYTES])));
        assertThatThrownBy(() -> JobFilePreviewController.stream(large, "doc.hwp", java.io.OutputStream.nullOutputStream())).isInstanceOf(java.io.IOException.class);
    }

    @Test void matchesMagicToTheDeclaredExtension() {
        byte[] zip = {0x50, 0x4b, 0x03, 0x04, 0, 0, 0, 0};
        assertThat(JobFilePreviewController.validMagic("공고.HWPX", zip)).isTrue();
        assertThat(JobFilePreviewController.validMagic("공고.hwp", zip)).isFalse();
        assertThat(JobFilePreviewController.validMagic("공고.hwpx", java.util.Arrays.copyOf(hwp, 8))).isFalse();
    }

    @Test void validatesRedirectBeforeFollowingIt() {
        var calls = new AtomicInteger();
        var web = WebClient.builder().exchangeFunction(request -> {
            calls.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.FOUND).header(HttpHeaders.LOCATION, "https://evil.example/secret").build());
        }).build();
        var controller = new JobFilePreviewController(null, new SimpleMeterRegistry(), web);
        assertThatThrownBy(() -> controller.fetch(URI.create("https://www.alio.go.kr/a"), "doc.hwp", 0).collectList().block()).hasRootCauseInstanceOf(java.io.IOException.class);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test void followsAllowedRedirectButStopsRedirectLoops() throws Exception {
        var calls = new AtomicInteger();
        var web = WebClient.builder().exchangeFunction(request -> {
            if (calls.incrementAndGet() == 1) return Mono.just(ClientResponse.create(HttpStatus.FOUND).header(HttpHeaders.LOCATION, "/download/final").build());
            return Mono.just(ClientResponse.create(HttpStatus.OK).body(Flux.just((org.springframework.core.io.buffer.DataBuffer) buffers.wrap(hwp))).build());
        }).build();
        var controller = new JobFilePreviewController(null, new SimpleMeterRegistry(), web);
        var output = new ByteArrayOutputStream();
        JobFilePreviewController.stream(controller.fetch(URI.create("https://www.alio.go.kr/a"), "doc.hwp", 0), "doc.hwp", output);
        assertThat(output.toByteArray()).isEqualTo(hwp);
        assertThat(calls.get()).isEqualTo(2);

        calls.set(0);
        var loop = WebClient.builder().exchangeFunction(request -> { calls.incrementAndGet(); return Mono.just(ClientResponse.create(HttpStatus.FOUND).header(HttpHeaders.LOCATION, "/again").build()); }).build();
        var loopController = new JobFilePreviewController(null, new SimpleMeterRegistry(), loop);
        assertThatThrownBy(() -> loopController.fetch(URI.create("https://www.alio.go.kr/a"), "doc.hwp", 0).collectList().block()).hasRootCauseInstanceOf(java.io.IOException.class);
        assertThat(calls.get()).isEqualTo(4);
    }

    @Test void enforcesDeclaredContentLength() {
        var web = WebClient.builder().exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_LENGTH, Long.toString(JobFilePreviewController.MAX_BYTES + 1)).build())).build();
        var controller = new JobFilePreviewController(null, new SimpleMeterRegistry(), web);
        assertThatThrownBy(() -> controller.fetch(URI.create("https://www.alio.go.kr/a"), "doc.hwp", 0).collectList().block()).hasRootCauseInstanceOf(java.io.IOException.class);
    }

    @Test void limitsConcurrentLargeStreams() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var rs = mock(java.sql.ResultSet.class);
        when(rs.getString(1)).thenReturn("공고.hwp");
        when(rs.getString(2)).thenReturn("https://www.alio.go.kr/download/download.json?fileNo=123");
        when(jdbc.query(anyString(), any(RowMapper.class), anyLong(), any(java.time.LocalDate.class))).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            return java.util.List.of(mapper.mapRow(rs, 0));
        });
        var entered = new java.util.concurrent.CountDownLatch(3);
        var release = new java.util.concurrent.CountDownLatch(1);
        byte[] chunk = new byte[64 * 1024];
        System.arraycopy(hwp, 0, chunk, 0, 8);
        var web = WebClient.builder().exchangeFunction(request -> Mono.fromCallable(() -> {
            entered.countDown();
            if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("test timed out");
            var body = Flux.range(0, 336).map(index -> (org.springframework.core.io.buffer.DataBuffer) buffers.wrap(chunk));
            return ClientResponse.create(HttpStatus.OK).body(body).build();
        })).build();
        var controller = new JobFilePreviewController(jdbc, new SimpleMeterRegistry(), web);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(3);
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 3; i++) futures.add(executor.submit(() -> {
                controller.preview(1).getBody().writeTo(java.io.OutputStream.nullOutputStream());
                return null;
            }));
            assertThat(entered.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> controller.preview(1))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("429");
            release.countDown();
            for (var future : futures) future.get(30, java.util.concurrent.TimeUnit.SECONDS);
        } finally { release.countDown(); executor.shutdownNow(); }
    }
}
