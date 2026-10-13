package kr.itsdev.devjobcollector.company.jobs;

import static org.assertj.core.api.Assertions.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CompanyJobContentTest {
    static CompanyJobContent.Input input(String title, String url, LocalDate end) {
        return new CompanyJobContent.Input(title, "backend", "신입", "서울", "정규직", "Java,Spring", "업무 내용", end, url);
    }
    @Test void draftAllowsIncompleteFieldsButSubmissionDoesNot() {
        var draft = CompanyJobContent.from(new CompanyJobContent.Input(null,null,null,null,null,null,null,null,null));
        assertThat(draft.getTitle()).isEmpty();
        assertThatThrownBy(() -> draft.validateSubmission(LocalDate.now())).hasMessageContaining("JOB_REQUIRED_FIELDS");
    }
    @Test void rejectsUnsafeAndCredentialBearingUrls() {
        for (String url : new String[]{"javascript:alert(1)", "http://example.com", "https://user:secret@example.com", "https://localhost"}) {
            var c=CompanyJobContent.from(input("개발자", url, LocalDate.now()));
            assertThatThrownBy(() -> c.validateSubmission(LocalDate.now())).hasMessageContaining("JOB_HTTPS_APPLY_URL_REQUIRED");
        }
    }
    @Test void closingDateIncludesTodayButNotYesterday() {
        assertThatCode(() -> CompanyJobContent.from(input("개발자", "https://example.com/jobs", LocalDate.now()))
                .validateSubmission(LocalDate.now())).doesNotThrowAnyException();
        assertThatThrownBy(() -> CompanyJobContent.from(input("개발자", "https://example.com/jobs", LocalDate.now().minusDays(1)))
                .validateSubmission(LocalDate.now())).hasMessageContaining("JOB_REQUIRED_FIELDS");
    }
}
