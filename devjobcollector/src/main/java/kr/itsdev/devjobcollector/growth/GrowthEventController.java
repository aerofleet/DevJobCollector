package kr.itsdev.devjobcollector.growth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/analytics/events")
public class GrowthEventController {
    public enum Kind { PAGE_VIEW, SEARCH, JOB_VIEW, BOOKMARK, APPLY_CLICK, SIGNUP, LOGIN, SHARE }
    public record Event(@NotNull UUID eventId, @NotNull UUID visitorId, @NotNull UUID sessionId,
                        @NotNull Kind kind,
                        @NotNull @Pattern(regexp = "home|jobs|job|login|signup|member|my-devjobs|resumes|resume|company|terms|privacy|other") String page,
                        @Positive Long jobId,
                        @NotNull @Pattern(regexp = "[a-z0-9_-]{1,48}") String source,
                        @NotNull @Pattern(regexp = "[a-z0-9_-]{1,48}") String medium,
                        @NotNull @Pattern(regexp = "[a-z0-9_-]{0,48}") String campaign) {}

    private final GrowthEventService service;
    private final Map<UUID, Integer> requests = new HashMap<>();
    private long minute;
    private int total;

    public GrowthEventController(GrowthEventService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void record(@Valid @RequestBody Event event) {
        checkLimit(event.visitorId());
        service.record(event);
    }

    private synchronized void checkLimit(UUID visitor) {
        long now = System.currentTimeMillis() / 60000;
        if (minute != now) { requests.clear(); total = 0; minute = now; }
        int count = requests.getOrDefault(visitor, 0);
        if (total >= 6000 || count >= 120) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        requests.put(visitor, count + 1);
        total++;
    }
}
