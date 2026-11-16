package kr.itsdev.devjobcollector.growth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/analytics/page-views")
public class BasicPageViewController {
    public record View(@NotNull @Pattern(regexp = "home|jobs|job|login|signup|member|my-devjobs|resumes|resume|company|terms|privacy|other") String page) {}
    private final GrowthEventService service;
    private long minute;
    private int total;

    public BasicPageViewController(GrowthEventService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void record(@Valid @RequestBody View view) {
        synchronized (this) {
            long now = System.currentTimeMillis() / 60000;
            if (minute != now) { minute = now; total = 0; }
            if (total >= 6000) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
            total++;
        }
        service.recordBasicPageView(view.page());
    }
}
