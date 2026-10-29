package kr.itsdev.devjobcollector.domain;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReviewedJobMetadataTest {
    @Test void reviewedValuesSurviveUnchangedRefreshAndExpireOnSourceChange() {
        var job = JobPost.builder().sourcePlatform(SourcePlatform.GREENHOUSE).originalSn("example:1")
                .companyName("Example").title("Sales Director").experience("경력무관").location("Remote")
                .applyQual("Requirements: sales leadership 5 years").startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2030, 1, 1)).originalUrl("https://example.com/job").build();
        job.prePersist();
        job.applyReviewedMetadata("EXPERIENCED", "경력 5~10년 이상", "sales leadership 5 years", "미국 뉴욕, 원격");
        job.refreshFromSource("Example", "Sales Director", null, "경력무관", "Remote", null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2030, 1, 1), "https://example.com/job", "Requirements: sales leadership 5 years", null);
        job.preUpdate();
        assertThat(job.getExperienceLabel()).isEqualTo("경력 5~10년 이상");
        assertThat(job.getDisplayLocation()).isEqualTo("미국 뉴욕, 원격");
        job.refreshFromSource("Example", "Sales Director", null, "경력무관", "Remote", null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2030, 1, 1), "https://example.com/job", "Updated requirements", null);
        job.preUpdate();
        assertThat(job.getExperienceLabel()).isEqualTo("경력 확인 필요");
        assertThat(job.getDisplayLocation()).isEqualTo("Remote");
    }
}
