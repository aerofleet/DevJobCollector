package kr.itsdev.devjobcollector.company.jobs;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.auth0.jwt.interfaces.*;
import jakarta.servlet.http.Cookie;
import java.util.Set;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.*;
import kr.itsdev.devjobcollector.config.PerfLogProperties;
import kr.itsdev.devjobcollector.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({CompanyJobController.class,AdminCompanyJobController.class})
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,AdminSecurityConfiguration.class,AdminTokenCodec.class})
class CompanyJobControllerSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired AdminTokenCodec codec;
    @MockitoBean CompanyJobService service;
    @MockitoBean AdminAuthenticationService authentication;
    @MockitoBean JwtTokenVerifier jwtTokenVerifier;
    @MockitoBean PerfLogProperties perfLogProperties;
    @Test void requiresMemberAuthenticationForDraftsAndMutations() throws Exception {
        mvc.perform(get("/api/v1/companies/1/jobs")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/companies/1/jobs").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void memberJwtCannotReviewCompanyJobs() throws Exception {
        mvc.perform(get("/api/v1/admin/jobs/company-submissions").header("Authorization","Bearer member-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service,jwtTokenVerifier);
    }
    @Test void adminReviewRequiresCsrf() throws Exception {
        when(authentication.authenticate("admin")).thenReturn(new AdminPrincipal(1L,"a@example.com","Admin",AdminRole.ADMIN,Set.of(),"session",codec.hash("csrf")));
        mvc.perform(post("/api/v1/admin/jobs/company-submissions/1/review")
                .cookie(new Cookie("DJC_ADMIN_SESSION","admin")).header("Origin","https://djc-admin.itsdev.kr")
                .contentType("application/json").content("{\"action\":\"APPROVE\",\"expectedVersion\":0,\"reason\":\"ok\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void historyRequiresAdminSessionButReviewerMayRead() throws Exception {
        mvc.perform(get("/api/v1/admin/jobs/company-submissions/history")).andExpect(status().isUnauthorized());
        when(authentication.authenticate("reviewer")).thenReturn(new AdminPrincipal(2L,"r@example.com","Reviewer",AdminRole.REVIEWER,Set.of(),"s",codec.hash("csrf")));
        when(service.reviewHistory(0,"DELETE",5L)).thenReturn(org.springframework.data.domain.Page.empty());
        mvc.perform(get("/api/v1/admin/jobs/company-submissions/history?action=DELETE&jobId=5")
                .cookie(new Cookie("DJC_ADMIN_SESSION","reviewer"))).andExpect(status().isOk());
        verify(service).reviewHistory(0,"DELETE",5L);
    }
    @Test void memberEditRequiresVersion() throws Exception {
        DecodedJWT jwt=mock(DecodedJWT.class); Claim role=mock(Claim.class);
        when(jwtTokenVerifier.verify("member")).thenReturn(jwt); when(jwt.getSubject()).thenReturn("42");
        when(jwt.getClaim("role")).thenReturn(role); when(role.asString()).thenReturn("USER");
        mvc.perform(put("/api/v1/companies/1/jobs/2").header("Authorization","Bearer member")
                .contentType("application/json").content("{\"content\":{\"title\":\"draft\"}}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
