package kr.itsdev.devjobcollector.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

class JobSearchMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private JobSearchMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new JobSearchMetrics(meterRegistry);
    }

    @Test
    void recordsHitWithoutExposingKeywordAsTag() {
        metrics.record("Java Kafka", () -> new PageImpl<>(java.util.List.of("job")));

        assertThat(counter("multi", "hit")).isEqualTo(1.0);
        assertThat(timer("multi", "hit").count()).isEqualTo(1);
        assertThat(meterRegistry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .map(tag -> tag.getValue()))
                .doesNotContain("Java Kafka", "java", "kafka");
    }

    @Test
    void recordsBlankAndEmptyWithBoundedTags() {
        metrics.record("  ", Page::empty);

        assertThat(counter("blank", "empty")).isEqualTo(1.0);
        assertThat(timer("blank", "empty").count()).isEqualTo(1);
    }

    @Test
    void recordsErrorsAndRethrows() {
        assertThatThrownBy(() -> metrics.record("Spring", () -> {
            throw new IllegalStateException("search failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(counter("single", "error")).isEqualTo(1.0);
        assertThat(timer("single", "error").count()).isEqualTo(1);
    }

    private double counter(String query, String outcome) {
        return meterRegistry.get(JobSearchMetrics.REQUEST_METRIC)
                .tags("query", query, "outcome", outcome)
                .counter()
                .count();
    }

    private io.micrometer.core.instrument.Timer timer(String query, String outcome) {
        return meterRegistry.get(JobSearchMetrics.DURATION_METRIC)
                .tags("query", query, "outcome", outcome)
                .timer();
    }
}
