package kr.itsdev.devjobcollector.service;

import kr.itsdev.devjobcollector.repository.JobPostRepository;
import kr.itsdev.devjobcollector.repository.TechStackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.itsdev.devjobcollector.dto.PublicJobDto;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.dto.PublicDataListResponse;
import kr.itsdev.devjobcollector.dto.PublicDataDetailResponse;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    void duplicateOnlyBatchIsSuccessfulAndDoesNotUseRequestedSizeAsDenominator() throws Exception {
        List<PublicJobDto> jobs = IntStream.range(0, 50).mapToObj(i -> job("id" + i, true)).toList();
        when(apiClient.fetchJobList(0, 100)).thenReturn(new PublicDataListResponse(200, "OK", 50, jobs));
        when(jobPostRepository.findExistingOriginalSns(any(), any())).thenReturn(
                jobs.stream().map(PublicJobDto::getRecrutPblntSn).toList());

        List<String> messages = captureSummary(() -> service.collectAll(100));

        assertThat(messages).anyMatch(s -> s.contains("수신 개수:") && s.contains("50"));
        assertThat(messages).anyMatch(s -> s.contains("처리 개수: 50 건 (미처리 0 건)"));
        assertThat(messages).anyMatch(s -> s.contains("처리 성공률: 100.0%"));
        assertThat(messages).anyMatch(s -> s.contains("신규율: 0.0%"));
        assertThat(messages).anyMatch(s -> s.contains("오류율: 0.0%"));
    }

    @Test
    void invalidRecordIsCountedAsErrorAlongsideDuplicate() {
        when(apiClient.fetchJobList(0, 100)).thenReturn(new PublicDataListResponse(200, "OK", 2,
                List.of(job("existing", true), job("invalid", false))));
        when(jobPostRepository.findExistingOriginalSns(any(), any())).thenReturn(List.of("existing"));

        List<String> messages = captureSummary(() -> service.collectAll(100));

        assertThat(messages).anyMatch(s -> s.contains("처리 성공률: 50.0%"));
        assertThat(messages).anyMatch(s -> s.contains("오류율: 50.0%"));
        assertThat(messages).anyMatch(s -> s.contains("신규율: 0.0%"));
    }

    @Test
    void failedListRequestDoesNotReportAZeroPercentSuccessRate() {
        when(apiClient.fetchJobList(0, 100)).thenReturn(null);

        List<String> messages = captureSummary(() -> service.collectAll(100));

        assertThat(messages).anyMatch(s -> s.contains("수집 상태: FAILED"));
        assertThat(messages).anyMatch(s -> s.contains("처리 성공률: N/A"));
        assertThat(messages).anyMatch(s -> s.contains("신규율: N/A"));
        assertThat(messages).anyMatch(s -> s.contains("오류율: N/A"));
    }

    @Test
    void failedBulkLookupReportsReceivedItemsAsUnprocessed() {
        when(apiClient.fetchJobList(0, 100)).thenReturn(new PublicDataListResponse(200, "OK", 2,
                List.of(job("first", true), job("second", true))));
        when(jobPostRepository.findExistingOriginalSns(any(), any())).thenThrow(new IllegalStateException("database unavailable"));

        List<String> messages = captureSummary(() -> service.collectAll(100));

        assertThat(messages).anyMatch(s -> s.contains("수집 상태: FAILED"));
        assertThat(messages).anyMatch(s -> s.contains("처리 개수: 0 건 (미처리 2 건)"));
        assertThat(messages).anyMatch(s -> s.contains("처리 성공률: N/A"));
    }

    @Test
    void newSaveContributesToBothSuccessfulProcessingAndNewRate() {
        PublicJobDto dto = job("new", true);
        dto.setPbancBgngYmd("20261101");
        dto.setPbancEndYmd("20261201");
        when(apiClient.fetchJobList(0, 100)).thenReturn(new PublicDataListResponse(200, "OK", 1, List.of(dto)));
        when(jobPostRepository.findExistingOriginalSns(any(), any())).thenReturn(List.of());
        when(apiClient.fetchJobDetail("new")).thenReturn(new PublicDataDetailResponse(200, "OK", 1, dto));
        JobPost saved = mock(JobPost.class);
        when(saved.getId()).thenReturn(1L);
        when(jobPostRepository.save(any(JobPost.class))).thenReturn(saved);

        List<String> messages = captureSummary(() -> service.collectAll(100));

        assertThat(messages).anyMatch(s -> s.contains("처리 성공률: 100.0%"));
        assertThat(messages).anyMatch(s -> s.contains("신규율: 100.0%"));
        assertThat(messages).anyMatch(s -> s.contains("오류율: 0.0%"));
    }

    @Test
    void duplicateDetectedDuringSaveIsASuccessfulSkip() {
        PublicJobDto dto = job("race", true);
        when(apiClient.fetchJobList(0, 100)).thenReturn(new PublicDataListResponse(200, "OK", 1, List.of(dto)));
        when(jobPostRepository.findExistingOriginalSns(any(), any())).thenReturn(List.of());
        when(apiClient.fetchJobDetail("race")).thenReturn(new PublicDataDetailResponse(200, "OK", 1, dto));
        when(jobPostRepository.existsBySourcePlatformAndOriginalSn(any(), any())).thenReturn(true);

        List<String> messages = captureSummary(() -> service.collectAll(100));

        assertThat(messages).anyMatch(s -> s.contains("처리 성공률: 100.0%"));
        assertThat(messages).anyMatch(s -> s.contains("오류율: 0.0%"));
        assertThat(messages).anyMatch(s -> s.contains("신규율: 0.0%"));
    }

    @Test
    void failedItemIsNotMistakenForDuplicateWhenItAppearsAgain() {
        PublicJobDto dto = job("retry", true);
        when(apiClient.fetchJobList(0, 100)).thenReturn(new PublicDataListResponse(200, "OK", 2, List.of(dto, dto)));
        when(jobPostRepository.findExistingOriginalSns(any(), any())).thenReturn(List.of());
        when(apiClient.fetchJobDetail("retry")).thenReturn(null);

        List<String> messages = captureSummary(() -> service.collectAll(100));

        assertThat(messages).anyMatch(s -> s.contains("처리 성공률: 0.0%"));
        assertThat(messages).anyMatch(s -> s.contains("오류율: 100.0%"));
    }

    private static PublicJobDto job(String id, boolean valid) {
        try {
            return new ObjectMapper().readValue("{\"recrutPblntSn\":\"" + id + "\",\"instNm\":\"회사\","
                    + "\"recrutPbancTtl\":\"제목\",\"srcUrl\":"
                    + (valid ? "\"https://example.com/job\"" : "null") + "}", PublicJobDto.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> captureSummary(Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(PublicDataCollectorService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
            return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
