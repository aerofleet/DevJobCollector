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
    @Test void optionalWorkAndLanguageChoicesAreRetainedAndProjectedToPublicText() {
        var content = CompanyJobContent.from(new CompanyJobContent.Input("개발자", "backend", "신입", "서울", "정규직", "Java", "업무 내용", LocalDate.now(), "https://example.com/jobs", "하이브리드", "비즈니스", "필수 아님", "검토 메모", "search"));
        assertThat(content.getWorkArrangement()).isEqualTo("하이브리드");
        assertThat(content.publicDescription()).isEqualTo("업무 내용\n\n[추가 근무 조건]\n근무 방식: 하이브리드\n한국어 수준: 비즈니스\n영어 수준: 필수 아님");
        assertThat(CompanyJobContent.from(input("개발자", "https://example.com/jobs", LocalDate.now())).publicDescription()).isEqualTo("업무 내용");
        var legacyEdit = CompanyJobContent.fromEdit(input("개발자", "https://example.com/jobs", LocalDate.now()), content);
        assertThat(legacyEdit.getWorkArrangement()).isEqualTo("하이브리드");
        assertThat(legacyEdit.getRecruiterNote()).isEqualTo("검토 메모");
        assertThat(legacyEdit.getReferralSource()).isEqualTo("search");
        var clear = CompanyJobContent.fromEdit(new CompanyJobContent.Input("개발자", "backend", "신입", "서울", "정규직", "Java", "업무 내용", LocalDate.now(), "https://example.com/jobs", "", "", "", "", ""), content);
        assertThat(clear.getWorkArrangement()).isEmpty();
        assertThat(clear.getRecruiterNote()).isEmpty();
    }
}
