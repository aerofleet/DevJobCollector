package kr.itsdev.devjobcollector.admin.companies;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import kr.itsdev.devjobcollector.admin.auth.AdminRequestSecurityFilter;
import kr.itsdev.devjobcollector.company.CompanyStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/companies")
public class AdminCompanyController {
    private final AdminCompanyService service;

    public AdminCompanyController(AdminCompanyService service) { this.service = service; }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) CompanyStatus status,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    HttpServletRequest request) {
        return Map.of("data", service.list(keyword, status, page, size),
                "requestId", request.getAttribute(AdminRequestSecurityFilter.REQUEST_ID_ATTRIBUTE));
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id, HttpServletRequest request) {
        return Map.of("data", service.detail(id),
                "requestId", request.getAttribute(AdminRequestSecurityFilter.REQUEST_ID_ATTRIBUTE));
    }
}
