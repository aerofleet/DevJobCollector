package kr.itsdev.devjobcollector.admin.companies;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import kr.itsdev.devjobcollector.admin.auth.AdminRequestSecurityFilter;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;

@RestController
@RequestMapping("/api/v1/admin/companies/discovery-candidates")
public class AdminDiscoveryController {
    private final AdminDiscoveryService service;

    public AdminDiscoveryController(AdminDiscoveryService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) String status,
                                                     @RequestParam(defaultValue = "0") long afterId,
                                                     @RequestParam(defaultValue = "20") int size,
                                                     HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
                "data", service.list(status, afterId, size),
                "requestId", request.getAttribute(AdminRequestSecurityFilter.REQUEST_ID_ATTRIBUTE)));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable long id, @RequestBody Rejection body,
                                       @AuthenticationPrincipal AdminPrincipal principal, HttpServletRequest request) {
        service.reject(id, body.reason(), principal,
                (String) request.getAttribute(AdminRequestSecurityFilter.REQUEST_ID_ATTRIBUTE),
                request.getRemoteAddr(), request.getHeader("User-Agent"));
        return ResponseEntity.noContent().build();
    }

    public record Rejection(String reason) { }
}
