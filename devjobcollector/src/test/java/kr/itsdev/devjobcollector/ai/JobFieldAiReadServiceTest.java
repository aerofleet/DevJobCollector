package kr.itsdev.devjobcollector.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.domain.SourcePlatform;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JobFieldAiReadServiceTest {
    @Test void reads_only_valid_current_approved_fields() {
        var jdbc = mock(JdbcTemplate.class); var job = mock(JobPost.class); var mapper = new ObjectMapper();
        when(job.getId()).thenReturn(1L); when(job.getVersion()).thenReturn(0L);
        when(job.getSourcePlatform()).thenReturn(SourcePlatform.PUBLIC_ALIO);
        when(job.getTitle()).thenReturn("전산 연구원"); when(job.getApplyQual()).thenReturn("시스템 개발과 연구 업무");
        var input = JobFieldAiProcessor.prepare(job, false);
        var row = new HashMap<String,Object>(Map.of("job_post_id", 1L, "job_version", 0L, "input_hash", input.inputHash(),
                "policy_version", JobFieldAiPolicy.VERSION, "model", "gpt-5.4-mini",
                "fields_json", "{\"fields\":[{\"field\":\"전산직\",\"evidence\":\"시스템 개발\"}]}"));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));
        assertThat(new JobFieldAiReadService(jdbc, mapper, false).approvedFields(List.of(job))).isEmpty();
        var service = new JobFieldAiReadService(jdbc, mapper, true);
        assertThat(service.approvedFields(List.of(job))).containsEntry(1L, List.of("전산직"));
        row.put("fields_json", "{\"fields\":[{\"field\":\"전산직\",\"evidence\":\"없는 근거\"}]}");
        assertThat(service.approvedFields(List.of(job))).isEmpty();
        row.put("fields_json", "{\"fields\":[{\"field\":\"전산직\",\"evidence\":\"시스템 개발\"}]}");
        row.put("job_version", 1L);
        assertThat(service.approvedFields(List.of(job))).isEmpty();
    }
}
