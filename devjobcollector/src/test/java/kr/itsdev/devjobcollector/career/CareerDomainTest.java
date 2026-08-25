package kr.itsdev.devjobcollector.career;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import org.junit.jupiter.api.Test;

class CareerDomainTest {
    private final UserAccount user = mock(UserAccount.class);
    private final JobPost jobPost = mock(JobPost.class);

    @Test
    void resumeRequiresContentAndSupportsStatusChange() {
        CareerResume resume = CareerResume.draft(user, " 기본 이력서 ", "{}");

        resume.changeStatus(ResumeStatus.READY);

        assertThat(resume.getTitle()).isEqualTo("기본 이력서");
        assertThat(resume.getStatus()).isEqualTo(ResumeStatus.READY);
        assertThatThrownBy(() -> resume.update(" ", "{}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void viewHistoryCountsOnlyChronologicalViews() {
        LocalDateTime firstViewedAt = LocalDateTime.of(2026, 8, 25, 10, 0);
        JobViewHistory history = JobViewHistory.firstView(user, jobPost, firstViewedAt);

        history.recordView(firstViewedAt.plusMinutes(5));

        assertThat(history.getViewCount()).isEqualTo(2);
        assertThat(history.getLastViewedAt()).isEqualTo(firstViewedAt.plusMinutes(5));
        assertThatThrownBy(() -> history.recordView(firstViewedAt.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void applicationNormalizesMemoAndChangesStatus() {
        JobApplication application = JobApplication.applied(
                user, jobPost, LocalDateTime.of(2026, 8, 25, 10, 0), "  서류 제출  ");

        application.changeStatus(ApplicationStatus.INTERVIEW);

        assertThat(application.getMemo()).isEqualTo("서류 제출");
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.INTERVIEW);
        assertThatThrownBy(() -> application.updateMemo("x".repeat(1001)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
