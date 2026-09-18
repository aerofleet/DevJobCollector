package kr.itsdev.devjobcollector.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import kr.itsdev.devjobcollector.repository.JobSearchKeyword;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobSearchMetrics {

    static final String REQUEST_METRIC = "djc.jobs.search.requests";
    static final String DURATION_METRIC = "djc.jobs.search.duration";

    private final MeterRegistry meterRegistry;

    public <T> Page<T> record(String keyword, Supplier<Page<T>> search) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String queryShape = queryShape(keyword);
        String outcome = "error";
        try {
            Page<T> result = search.get();
            outcome = result.isEmpty() ? "empty" : "hit";
            return result;
        } finally {
            Counter.builder(REQUEST_METRIC)
                    .tag("query", queryShape)
                    .tag("outcome", outcome)
                    .register(meterRegistry)
                    .increment();
            sample.stop(Timer.builder(DURATION_METRIC)
                    .tag("query", queryShape)
                    .tag("outcome", outcome)
                    .publishPercentileHistogram()
                    .register(meterRegistry));
        }
    }

    private String queryShape(String keyword) {
        int tokenCount = JobSearchKeyword.tokens(keyword).size();
        if (tokenCount == 0) {
            return "blank";
        }
        return tokenCount == 1 ? "single" : "multi";
    }
}
