package kr.itsdev.devjobcollector.service;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.itsdev.devjobcollector.dto.JobPostDto;
import kr.itsdev.devjobcollector.dto.JobPostDetailDto;
import static org.assertj.core.api.Assertions.assertThat;

class JobRecruitmentTypeTest {
    @Test void exposesTheSameFlagInListAndDetail() throws Exception {
        var mapper = new ObjectMapper();
        assertThat(mapper.valueToTree(JobPostDto.builder().industrialPersonnelRecruitment(true).build()).get("industrialPersonnelRecruitment").asBoolean()).isTrue();
        assertThat(mapper.valueToTree(JobPostDetailDto.builder().industrialPersonnelRecruitment(true).build()).get("industrialPersonnelRecruitment").asBoolean()).isTrue();
    }

    @Test void detectsExplicitCampaignAndRestrictedEligibility() {
        assertThat(JobRecruitmentType.industrialPersonnel("[산업기능요원 집중 채용 캠페인] Backend", "")).isTrue();
        assertThat(JobRecruitmentType.industrialPersonnel("Software Engineer, Backend", "지원 가능 유형은 보충역 신규 편입, 보충역 전직, 현역 전직이에요.\n산업기능요원도 동료입니다.")).isTrue();
    }

    @Test void ignoresIncidentalMentionsAndGeneralEligibility() {
        assertThat(JobRecruitmentType.industrialPersonnel("백엔드 개발자", "산업기능요원 지원 가능 및 우대" )).isFalse();
        assertThat(JobRecruitmentType.industrialPersonnel("백엔드 개발자", "팀원이 산업기능요원으로 복무했습니다." )).isFalse();
        assertThat(JobRecruitmentType.industrialPersonnel("백엔드 개발자", "병역필 또는 면제자" )).isFalse();
        assertThat(JobRecruitmentType.industrialPersonnel(null, null)).isFalse();
    }
}
