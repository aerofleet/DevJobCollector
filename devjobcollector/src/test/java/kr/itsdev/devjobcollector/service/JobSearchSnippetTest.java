package kr.itsdev.devjobcollector.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.domain.SourcePlatform;
import org.junit.jupiter.api.Test;

class JobSearchSnippetTest {

    @Test
    void returnsCollapsedDescriptionContextForSynonymMatch() {
        JobPost jobPost = job("서비스 소개\n\nKubernetes 기반 플랫폼을 운영합니다.", null);

        assertThat(JobSearchSnippet.from(jobPost, "k8s"))
                .isEqualTo("서비스 소개 Kubernetes 기반 플랫폼을 운영합니다.");
    }

    @Test
    void fallsBackToRecruitmentProcess() {
        JobPost jobPost = job("Java 개발자를 찾습니다.", "서류 검토 후 기술면접을 진행합니다.");

        assertThat(JobSearchSnippet.from(jobPost, "기술면접"))
                .isEqualTo("서류 검토 후 기술면접을 진행합니다.");
    }

    @Test
    void returnsNullWhenBodyDoesNotMatchOrKeywordIsBlank() {
        JobPost jobPost = job("Java 개발자를 찾습니다.", null);

        assertThat(JobSearchSnippet.from(jobPost, "Kafka")).isNull();
        assertThat(JobSearchSnippet.from(jobPost, "  ")).isNull();
    }

    @Test
    void limitsSnippetLengthAndAddsEllipsis() {
        JobPost jobPost = job("앞".repeat(140) + " Kafka " + "뒤".repeat(140), null);

        assertThat(JobSearchSnippet.from(jobPost, "Kafka"))
                .startsWith("…")
                .endsWith("…")
                .hasSizeLessThanOrEqualTo(JobSearchSnippet.MAX_LENGTH + 2);
    }

    private static JobPost job(String applyQual, String processInfo) {
        return JobPost.builder()
                .sourcePlatform(SourcePlatform.SARAMIN)
                .originalSn("snippet-job")
                .companyName("검색 평가 기업")
                .title("백엔드 개발자")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(30))
                .originalUrl("https://example.com/jobs/snippet-job")
                .applyQual(applyQual)
                .processInfo(processInfo)
                .build();
    }
}
