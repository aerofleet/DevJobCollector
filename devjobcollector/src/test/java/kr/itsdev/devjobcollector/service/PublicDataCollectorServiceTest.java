package kr.itsdev.devjobcollector.service;

import kr.itsdev.devjobcollector.repository.JobPostRepository;
import kr.itsdev.devjobcollector.repository.TechStackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PublicDataCollectorServiceTest {

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
