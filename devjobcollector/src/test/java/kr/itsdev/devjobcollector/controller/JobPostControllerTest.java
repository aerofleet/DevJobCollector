package kr.itsdev.devjobcollector.controller;

import kr.itsdev.devjobcollector.monitoring.RequestTimingInterceptor;
import kr.itsdev.devjobcollector.security.JwtTokenVerifier;
import kr.itsdev.devjobcollector.service.JobPostService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobPostController.class)
@AutoConfigureMockMvc(addFilters = false)
class JobPostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobPostService jobPostService;

    @MockitoBean
    private RequestTimingInterceptor requestTimingInterceptor;

    @MockitoBean
    private JwtTokenVerifier jwtTokenVerifier;

    @BeforeEach
    void allowRequestThroughTimingInterceptor() throws Exception {
        when(requestTimingInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    void forwardsExplorerFiltersAndDeadlineSort() throws Exception {
        when(jobPostService.searchJobPosts(
                any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/jobs/search")
                        .param("keyword", "플랫폼")
                        .param("location", "서울")
                        .param("experience", "경력")
                        .param("jobCategory", "backend")
                        .param("techStack", "Java")
                        .param("sortBy", "endDate")
                        .param("direction", "ASC")
                        .param("page", "2")
                        .param("size", "12"))
                .andExpect(status().isOk());

        var pageableCaptor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(jobPostService).searchJobPosts(
                eq("플랫폼"), eq("서울"), eq("경력"), eq("backend"), eq("Java"),
                pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(12);
        assertThat(pageable.getSort().getOrderFor("endDate")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("endDate").isAscending()).isTrue();
    }
}
