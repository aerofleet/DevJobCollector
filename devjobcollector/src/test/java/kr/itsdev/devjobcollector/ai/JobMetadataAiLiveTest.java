package kr.itsdev.devjobcollector.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.Set;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.admin.jobs.JobMetadataAiService;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.domain.SourcePlatform;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;

/** One billable request using only synthetic job text. */
@EnabledIfEnvironmentVariable(named = "DJC_OPENAI_LIVE_TEST", matches = "true")
class JobMetadataAiLiveTest {
    @Test void modelReturnsGroundedExperienceAndLocationFromSyntheticPosting() {
        String input = "자격 요건: 백엔드 개발 실무 경력 3년 이상. 우대 사항: 머신러닝 7년 경험. 근무지: 서울 강남구.";
        var client = new OpenAiEvidenceClient(new ObjectMapper(), System.getenv("OPENAI_API_KEY"), true);
        var jobs = mock(JobPostRepository.class);
        var jdbc = mock(JdbcTemplate.class);
        var job = mock(JobPost.class);
        when(job.getId()).thenReturn(42L);
        when(job.getVersion()).thenReturn(1L);
        when(job.getSourcePlatform()).thenReturn(SourcePlatform.GREENHOUSE);
        when(job.getOriginalSn()).thenReturn("synthetic:42");
        when(job.getApplyQual()).thenReturn(input);
        when(job.getExperienceCategory()).thenReturn("UNKNOWN");
        when(jobs.findById(42L)).thenReturn(Optional.of(job));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        var service = new JobMetadataAiService(jobs, client, jdbc, true, "GREENHOUSE:synthetic");
        var admin = new AdminPrincipal(1L, "synthetic@example.test", "admin", AdminRole.ADMIN, Set.of(), "session", "csrf");
        var preview = service.preview(42L, admin);
        var result = service.analyze(42L, preview.inputHash(), true, admin);
        assertThat(result).containsEntry("status", "NEEDS_REVIEW");
        assertThat((String) result.get("experience")).contains("3");
        assertThat((String) result.get("experienceQuote")).isNotBlank().satisfies(quote -> assertThat(input).contains(quote));
        assertThat((String) result.get("location")).contains("서울");
        assertThat((String) result.get("locationQuote")).isNotBlank().satisfies(quote -> assertThat(input).contains(quote));
        verify(jdbc).update(anyString(), any(Object[].class));
    }
}
