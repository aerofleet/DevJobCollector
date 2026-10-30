package kr.itsdev.devjobcollector.admin.jobs;

import kr.itsdev.devjobcollector.admin.auth.AdminAuthenticationService;
import kr.itsdev.devjobcollector.admin.auth.AdminSecurityConfiguration;
import kr.itsdev.devjobcollector.admin.auth.AdminTokenCodec;
import kr.itsdev.devjobcollector.config.PerfLogProperties;
import kr.itsdev.devjobcollector.security.JwtAuthenticationFilter;
import kr.itsdev.devjobcollector.security.JwtTokenVerifier;
import kr.itsdev.devjobcollector.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StripeSalaryPreviewController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, AdminSecurityConfiguration.class, AdminTokenCodec.class})
class StripeSalaryPreviewControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean StripeSalaryPreviewService service;
    @MockitoBean AdminAuthenticationService authentication;
    @MockitoBean JwtTokenVerifier verifier;
    @MockitoBean PerfLogProperties performance;

    @Test void anonymousCannotPreview() throws Exception {
        mvc.perform(get("/api/v1/admin/jobs/11051/stripe-salary/preview")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

}
