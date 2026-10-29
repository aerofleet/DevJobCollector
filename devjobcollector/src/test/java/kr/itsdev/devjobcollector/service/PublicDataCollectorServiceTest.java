package kr.itsdev.devjobcollector.service;

import kr.itsdev.devjobcollector.repository.JobPostRepository;
import kr.itsdev.devjobcollector.repository.TechStackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.itsdev.devjobcollector.dto.PublicJobDto;
import kr.itsdev.devjobcollector.domain.JobPost;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PublicDataCollectorServiceTest {

    @Test
    void mapsPublicDetailFieldsWithoutLosingEligibility() throws Exception {
        PublicJobDto dto = new ObjectMapper().readValue("""
                {"prefCn":"우대 사항", "prefCondCn":"우대 조건", "disqlfcRsn":"결격 사유", "aplyQlfcCn":"지원 자격", "files":[{"atchFileNm":"안내.pdf","url":"https://example.com/file.pdf"}]}
                """, PublicJobDto.class);
        JobPost job = JobPost.builder().applyQual(dto.getAplyQlfcCn()).build();
        job.updatePublicDetails(dto.getPrefCn(), dto.getPrefCondCn(), dto.getDisqlfcRsn());
        assertThat(job.getApplyQual()).isEqualTo("지원 자격");
        assertThat(job.getPublicPreference()).isEqualTo("우대 사항");
        assertThat(job.getPublicPreferenceConditions()).isEqualTo("우대 조건");
        assertThat(job.getPublicDisqualification()).isEqualTo("결격 사유");
        assertThat(dto.getFiles()).singleElement().satisfies(file -> assertThat(file.isValid()).isTrue());
    }

    @Mock
    private JobPostRepository jobPostRepository;

    @Mock
    private TechStackRepository techStackRepository;

    @Mock
    private PublicDataApiClient apiClient;

    @InjectMocks
    private PublicDataCollectorService service;

    @BeforeEach
    void disableCollection() {
        ReflectionTestUtils.setField(service, "collectionEnabled", false);
    }

    @Test
    void skipsStartupCollectionWhenDisabled() {
        service.initCollect();

        verifyNoInteractions(apiClient, jobPostRepository, techStackRepository);
    }

    @Test
    void skipsScheduledCollectionWhenDisabled() {
        service.scheduleCollect();

        verifyNoInteractions(apiClient, jobPostRepository, techStackRepository);
    }
}
