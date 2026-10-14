package kr.itsdev.devjobcollector.growth;

import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/dashboard/growth")
public class GrowthDashboardController {
    private final GrowthEventService service;
    public GrowthDashboardController(GrowthEventService service) { this.service = service; }
    @GetMapping
    public Map<String, Object> summary() { return service.summary(); }
}
