package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.itsdev.devjobcollector.career.ResumeService;
import kr.itsdev.devjobcollector.dto.career.ResumeDetailResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResumePreprocessingServiceTest {
    private final ResumeService resumes = mock(ResumeService.class);
    private final ResumeTextExtractor extractor = mock(ResumeTextExtractor.class);
    private final ResumePreprocessingService service = new ResumePreprocessingService(resumes, extractor, new ResumePiiGate());

    @Test
    void requiresConsentBeforeReadingResume() {
        assertThatThrownBy(() -> service.preview("42", 1L, false, null)).hasMessageContaining("CONSENT_REQUIRED");
        verifyNoInteractions(resumes, extractor);
    }

    @Test
    void preventsNonOwnersFromStartingParser() {
        when(resumes.get("42", 1L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.preview("42", 1L, true,
                new MockMultipartFile("file", new byte[]{1}))).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(extractor);
    }

    @Test
    void omitsBasicInfoAndRedactsKnownIdentityInProjectText() throws Exception {
        var content = new ObjectMapper().readTree("""
                {"basicInfo":{"name":"홍길동","email":"private@example.com","birthDate":"1990-01-01"},
                 "techStack":["Java"],"projects":[{"description":"홍길동의 Spring 프로젝트"}],
                 "experience":[],"unknown":"secret"}
                """);
        when(resumes.get("42", 1L)).thenReturn(new ResumeDetailResponse(1L, "title", null, null, content, null, null));
        var result = service.preview("42", 1L, true, null);
        assertThat(result.maskedText()).contains("Java", "Spring").doesNotContain("홍길동", "@", "1990", "secret");
        assertThat(result.piiGatePassed()).isFalse();
        verifyNoInteractions(extractor);
    }
}
