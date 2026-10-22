package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecommendationFeedbackTest {
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final CurrentMemberService members = mock(CurrentMemberService.class);
    final ResumeAnalysisService service = new ResumeAnalysisService(jdbc, members,
            mock(LocalRecommendationEngine.class), new ObjectMapper());

    @BeforeEach void setup() {
        var user = mock(UserAccount.class);
        when(user.getId()).thenReturn(42L);
        when(members.requireCurrentMember("owner")).thenReturn(user);
        when(jdbc.queryForList(anyString(), eq(1L), eq(42L))).thenReturn(List.of(Map.of(
                "resume_id", 2L, "status", "COMPLETED_LOCAL", "created_at", "2026-10-22",
                "result_json", """
                {"recommendations":[{"jobId":17,"rank":1}],"aiReview":{"jobs":[{"jobId":17}]}}
                """)));
        when(jdbc.queryForList(anyString(), eq(17L), any(LocalDate.class)))
                .thenReturn(List.of(Map.of("title", "Backend")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CAREER_MISMATCH", "SKILL_MISMATCH", "LOCATION_MISMATCH", "ALREADY_SEEN", "NOT_INTERESTED", "INACCURATE_JOB"})
    void excludesNegativeFeedbackOnEveryReadAndAllowsRestoration(String type) {
        when(jdbc.queryForList(anyString(), eq(String.class), eq(1L), eq(17L))).thenReturn(List.of(type));
        var result = service.get("owner", 1);
        assertThat(result.path("recommendations")).isEmpty();
        assertThat(result.path("aiReview").path("jobs")).isEmpty();
        assertThat(result.path("excludedRecommendations").get(0).path("feedback").asText()).isEqualTo(type);
        service.feedback("owner", 1, 17, "MATCHED");
        verify(jdbc).update(contains("INSERT INTO ai_recommendation_feedback"), eq(1L), eq(17L), eq("MATCHED"));
    }

    @Test void matchedAndUnratedJobsRemainVisible() {
        when(jdbc.queryForList(anyString(), eq(String.class), eq(1L), eq(17L)))
                .thenReturn(List.of(), List.of("MATCHED"));
        for (int i = 0; i < 2; i++) {
            var result = service.get("owner", 1);
            assertThat(result.path("recommendations")).hasSize(1);
            assertThat(result.path("excludedRecommendations")).isEmpty();
            assertThat(result.path("aiReview").path("jobs")).hasSize(1);
        }
    }

    @Test void matchedFeedbackDoesNotRestoreUnavailableJobs() {
        when(jdbc.queryForList(anyString(), eq(17L), any(LocalDate.class))).thenReturn(List.of());
        assertThat(service.get("owner", 1).path("recommendations")).isEmpty();
        assertThatThrownBy(() -> service.feedback("owner", 1, 17, "MATCHED"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.feedback("owner", 1, 999, "MATCHED"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test void rejectsMissingFeedbackAndOtherMembers() {
        assertThatThrownBy(() -> service.feedback("owner", 1, 17, null))
                .isInstanceOf(ResponseStatusException.class);
        var other = mock(UserAccount.class);
        when(other.getId()).thenReturn(99L);
        when(members.requireCurrentMember("other")).thenReturn(other);
        assertThatThrownBy(() -> service.feedback("other", 1, 17, "MATCHED"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
