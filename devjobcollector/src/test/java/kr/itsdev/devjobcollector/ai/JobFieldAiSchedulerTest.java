package kr.itsdev.devjobcollector.ai;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JobFieldAiSchedulerTest {
    @Test void disabled_batch_does_not_query_or_generate() {
        var jdbc = mock(JdbcTemplate.class); var processor = mock(JobFieldAiProcessor.class);
        new JobFieldAiScheduler(jdbc, processor, false).collect();
        verifyNoInteractions(jdbc, processor);
    }
    @Test void daily_limit_stops_batch_without_losing_the_candidate() {
        var jdbc = mock(JdbcTemplate.class); var processor = mock(JobFieldAiProcessor.class);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of("id", 5L)));
        when(processor.process(5L)).thenReturn(JobFieldAiProcessor.Outcome.DAILY_LIMIT);
        var scheduler = new JobFieldAiScheduler(jdbc, processor, true);
        scheduler.collect(); scheduler.collect();
        verify(processor, times(2)).process(5L);
        verify(jdbc).queryForList(anyString(), eq(0L), eq(JobFieldAiPolicy.VERSION));
        verify(jdbc).queryForList(anyString(), eq(4L), eq(JobFieldAiPolicy.VERSION));
    }
}
