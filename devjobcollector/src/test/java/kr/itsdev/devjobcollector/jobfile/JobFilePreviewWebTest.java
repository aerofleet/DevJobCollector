package kr.itsdev.devjobcollector.jobfile;

import io.micrometer.core.instrument.MeterRegistry;
import kr.itsdev.devjobcollector.monitoring.RequestTimingInterceptor;
import kr.itsdev.devjobcollector.security.JwtTokenVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobFilePreviewController.class)
@AutoConfigureMockMvc(addFilters = false)
class JobFilePreviewWebTest {
    @Autowired MockMvc mvc;
    @MockitoBean JdbcTemplate jdbc;
    @MockitoBean MeterRegistry metrics;
    @MockitoBean RequestTimingInterceptor timing;
    @MockitoBean JwtTokenVerifier verifier;

    @Test void returnsNotFoundForUnknownFile() throws Exception {
        when(timing.preHandle(any(), any(), any())).thenReturn(true);
        mvc.perform(get("/api/v1/job-files/999/preview")).andExpect(status().isNotFound());
    }
}
