package kr.itsdev.devjobcollector.config;

import kr.itsdev.devjobcollector.monitoring.RequestTimingInterceptor;
import kr.itsdev.devjobcollector.security.JwtTokenVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WebConfigCorsTest.CorsProbeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(WebConfig.class)
class WebConfigCorsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RequestTimingInterceptor requestTimingInterceptor;

    @MockitoBean
    private JwtTokenVerifier jwtTokenVerifier;

    @Test
    void allowsDjcFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/cors-probe")
                        .header(HttpHeaders.ORIGIN, "https://djc.itsdev.kr")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "https://djc.itsdev.kr"));
    }

    @Test
    void rejectsWithBuddyFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/cors-probe")
                        .header(HttpHeaders.ORIGIN, "https://withbuddy.itsdev.kr")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @RestController
    static class CorsProbeController {

        @GetMapping("/api/cors-probe")
        String probe() {
            return "ok";
        }
    }
}
