package kr.itsdev.devjobcollector.admin.companies;

import jakarta.servlet.http.Cookie;
import java.util.Set;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminAuthenticationService;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.admin.auth.AdminSecurityConfiguration;
import kr.itsdev.devjobcollector.admin.auth.AdminTokenCodec;
import kr.itsdev.devjobcollector.collection.discovery.SmeJobCompanySeedReader;
import kr.itsdev.devjobcollector.config.PerfLogProperties;
import kr.itsdev.devjobcollector.security.JwtAuthenticationFilter;
import kr.itsdev.devjobcollector.security.JwtTokenVerifier;
import kr.itsdev.devjobcollector.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminPublicSeedController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, AdminSecurityConfiguration.class, AdminTokenCodec.class})
class AdminPublicSeedControllerSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired AdminTokenCodec tokenCodec;
    @MockitoBean AdminPublicSeedImportService service;
    @MockitoBean AdminPublicSeedQueryService query;
    @MockitoBean AdminPublicSeedReviewService review;
    @MockitoBean AdminPublicSeedDiscoveryService discovery;
    @MockitoBean PublicSeedBoardVerificationService verification;
    @MockitoBean PublicSeedTargetApprovalService approval;
    @MockitoBean AdminAuthenticationService authenticationService;
    @MockitoBean JwtTokenVerifier jwtTokenVerifier;
    @MockitoBean PerfLogProperties perfLogProperties;

    @Test
    void listRequiresAdminSession() throws Exception {
        mvc.perform(get("/api/v1/admin/companies/public-seeds")).andExpect(status().isUnauthorized());
        verifyNoInteractions(query);
    }

    @Test
    void homepageUpdateRequiresCsrf() throws Exception {
        when(authenticationService.authenticate("token")).thenReturn(new AdminPrincipal(1L, "admin@example.com",
                "Admin", AdminRole.ADMIN, Set.of(), "session", tokenCodec.hash("csrf")));
        String path = "/api/v1/admin/companies/public-seeds/1/homepage";
        mvc.perform(put(path).cookie(new Cookie("DJC_ADMIN_SESSION", "token"))
                .header("Origin", "https://djc-admin.itsdev.kr").contentType("application/json")
                .content("{\"url\":\"https://example.com\",\"expectedVersion\":0}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(review);
        mvc.perform(put(path).cookie(new Cookie("DJC_ADMIN_SESSION", "token"))
                .header("Origin", "https://djc-admin.itsdev.kr").header("X-CSRF-TOKEN", "csrf")
                .contentType("application/json").content("{\"url\":\"https://example.com\",\"expectedVersion\":0}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void homepageDiscoveryRequiresCsrf() throws Exception {
        when(authenticationService.authenticate("token")).thenReturn(new AdminPrincipal(1L, "admin@example.com",
                "Admin", AdminRole.ADMIN, Set.of(), "session", tokenCodec.hash("csrf")));
        String path = "/api/v1/admin/companies/public-seeds/1/discover";
        mvc.perform(post(path).cookie(new Cookie("DJC_ADMIN_SESSION", "token"))
                .header("Origin", "https://djc-admin.itsdev.kr").contentType("application/json")
                .content("{\"html\":\"text\",\"expectedVersion\":0}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(discovery);
        mvc.perform(post(path).cookie(new Cookie("DJC_ADMIN_SESSION", "token"))
                .header("Origin", "https://djc-admin.itsdev.kr").header("X-CSRF-TOKEN", "csrf")
                .contentType("application/json").content("{\"html\":\"text\",\"expectedVersion\":0}"))
                .andExpect(status().isOk());
    }

    @Test
    void candidateRejectionRequiresCsrf() throws Exception {
        when(authenticationService.authenticate("token")).thenReturn(new AdminPrincipal(1L, "admin@example.com",
                "Admin", AdminRole.REVIEWER, Set.of(), "session", tokenCodec.hash("csrf")));
        String path = "/api/v1/admin/companies/public-seeds/1/ats-candidates/2/reject";
        mvc.perform(post(path).cookie(new Cookie("DJC_ADMIN_SESSION", "token"))
                .header("Origin", "https://djc-admin.itsdev.kr").contentType("application/json")
                .content("{\"reason\":\"소유권 미확인\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(discovery);
        mvc.perform(post(path).cookie(new Cookie("DJC_ADMIN_SESSION", "token"))
                .header("Origin", "https://djc-admin.itsdev.kr").header("X-CSRF-TOKEN", "csrf")
                .contentType("application/json").content("{\"reason\":\"소유권 미확인\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void reviewRequiresCsrf() throws Exception {
        when(authenticationService.authenticate("token")).thenReturn(new AdminPrincipal(1L, "admin@example.com",
                "Admin", AdminRole.REVIEWER, Set.of(), "session", tokenCodec.hash("csrf")));
        String path = "/api/v1/admin/companies/public-seeds/1/ats-candidates/2/review";
        String json = "{\"note\":\"확인 중\",\"expectedSeedVersion\":1}";
        mvc.perform(post(path).cookie(new Cookie("DJC_ADMIN_SESSION", "token"))
                .header("Origin", "https://djc-admin.itsdev.kr").contentType("application/json").content(json))
                .andExpect(status().isForbidden());
        verifyNoInteractions(discovery);
        mvc.perform(post(path).cookie(new Cookie("DJC_ADMIN_SESSION", "token"))
                .header("Origin", "https://djc-admin.itsdev.kr").header("X-CSRF-TOKEN", "csrf")
                .contentType("application/json").content(json)).andExpect(status().isNoContent());
    }

    @Test
    void boardVerificationRequiresCsrf() throws Exception {
        when(authenticationService.authenticate("token")).thenReturn(new AdminPrincipal(1L, "admin@example.com",
                "Admin", AdminRole.REVIEWER, Set.of(), "session", tokenCodec.hash("csrf")));
        String path = "/api/v1/admin/companies/public-seeds/1/ats-candidates/2/verify-board";
        String json = "{\"expectedSeedVersion\":1}";
        mvc.perform(post(path).cookie(new Cookie("DJC_ADMIN_SESSION", "token"))
                .header("Origin", "https://djc-admin.itsdev.kr").contentType("application/json").content(json))
                .andExpect(status().isForbidden());
        verifyNoInteractions(verification);
        mvc.perform(post(path).cookie(new Cookie("DJC_ADMIN_SESSION", "token"))
                .header("Origin", "https://djc-admin.itsdev.kr").header("X-CSRF-TOKEN", "csrf")
                .contentType("application/json").content(json)).andExpect(status().isOk());
    }

    @Test
    void targetApprovalRequiresCsrf() throws Exception {
        when(authenticationService.authenticate("token")).thenReturn(new AdminPrincipal(1L, "admin@example.com",
                "Admin", AdminRole.ADMIN, Set.of(), "session", tokenCodec.hash("csrf")));
        String path = "/api/v1/admin/companies/public-seeds/1/ats-candidates/2/approve-target";
        String json = "{\"expectedSeedVersion\":1,\"companyName\":\"기업\",\"ownershipConfirmed\":true}";
        mvc.perform(post(path).cookie(new Cookie("DJC_ADMIN_SESSION", "token"))
                .header("Origin", "https://djc-admin.itsdev.kr").contentType("application/json").content(json))
                .andExpect(status().isForbidden());
        verifyNoInteractions(approval);
        mvc.perform(post(path).cookie(new Cookie("DJC_ADMIN_SESSION", "token"))
                .header("Origin", "https://djc-admin.itsdev.kr").header("X-CSRF-TOKEN", "csrf")
                .contentType("application/json").content(json)).andExpect(status().isOk());
    }

    @Test
    void importRequiresSessionAndCsrf() throws Exception {
        String path = "/api/v1/admin/companies/public-seeds/import";
        var file = new MockMultipartFile("file", "seed.csv", "text/csv", new byte[]{1});
        mvc.perform(multipart(path).file(file)).andExpect(status().isForbidden());
        when(authenticationService.authenticate("token")).thenReturn(new AdminPrincipal(1L, "admin@example.com",
                "Admin", AdminRole.ADMIN, Set.of(), "session", tokenCodec.hash("csrf")));
        mvc.perform(multipart(path).file(file).cookie(new Cookie("DJC_ADMIN_SESSION", "token"))
                .header("Origin", "https://djc-admin.itsdev.kr")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
        when(service.importFile(any(), anyString(), any(), anyString(), any(), any()))
                .thenReturn(new SmeJobCompanySeedReader.Counts(1, 0));
        mvc.perform(multipart(path).file(file).cookie(new Cookie("DJC_ADMIN_SESSION", "token"))
                .header("Origin", "https://djc-admin.itsdev.kr").header("X-CSRF-TOKEN", "csrf"))
                .andExpect(status().isOk());
    }
}
