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
    @Test void searchEvidenceIsOptionalAndRejectsFutureOriginalDates() {
        var today = LocalDate.of(2026, 10, 31);
        var valid = CompanyJobContent.from(new CompanyJobContent.Input("개발자", "backend", "신입", "서울", "정규직", "Java", "업무와 자격", today.plusDays(5), "https://example.com/jobs", today.minusDays(10), true));
        assertThat(valid.getOriginalPostedDate()).isEqualTo(today.minusDays(10));
        assertThat(valid.isCompleteDescriptionConfirmed()).isTrue();
        assertThatCode(() -> valid.validateSubmission(today)).doesNotThrowAnyException();
        var invalid = CompanyJobContent.from(new CompanyJobContent.Input("개발자", "backend", "신입", "서울", "정규직", "Java", "업무와 자격", today.plusDays(5), "https://example.com/jobs", today.plusDays(1), true));
        assertThatThrownBy(() -> invalid.validateSubmission(today)).hasMessageContaining("JOB_INVALID_POSTED_DATE");
    }
    @Test void optionalSearchFieldsDeserializeFromCompanyForm() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var input = mapper.readValue("{\"title\":\"Dev\",\"originalPostedDate\":\"2026-10-01\",\"completeDescriptionConfirmed\":true}", CompanyJobContent.Input.class);
        assertThat(input.originalPostedDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(input.completeDescriptionConfirmed()).isTrue();
    }
}
