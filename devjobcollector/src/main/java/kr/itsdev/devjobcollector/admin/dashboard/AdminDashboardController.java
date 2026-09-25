package kr.itsdev.devjobcollector.admin.dashboard;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import kr.itsdev.devjobcollector.admin.auth.AdminRequestSecurityFilter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class AdminDashboardController {
    private final AdminDashboardService dashboardService;

    public AdminDashboardController(AdminDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(HttpServletRequest request) {
        return Map.of(
                "data", dashboardService.summary(),
                "requestId", request.getAttribute(
                        AdminRequestSecurityFilter.REQUEST_ID_ATTRIBUTE));
    }
}
