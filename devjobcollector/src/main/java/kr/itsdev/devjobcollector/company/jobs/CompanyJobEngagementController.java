package kr.itsdev.devjobcollector.company.jobs;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import kr.itsdev.devjobcollector.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/v1/jobs/{id}/engagement")
public class CompanyJobEngagementController {
    public enum Kind { VIEW, APPLY_CLICK }
    public record Event(@NotNull UUID eventId, @NotNull Kind kind) {}
    private final JobPostRepository posts;
    private final JdbcTemplate jdbc;
    public CompanyJobEngagementController(JobPostRepository posts, JdbcTemplate jdbc) { this.posts=posts; this.jdbc=jdbc; }
    @PostMapping @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    public void record(@PathVariable Long id, @Valid @RequestBody Event event) {
        JobPost post=posts.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (post.getSourcePlatform()!=SourcePlatform.COMPANY_DIRECT || !post.isActive()
                || post.getModerationStatus()!=JobModerationStatus.ACTIVE || post.getEndDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        jdbc.update("INSERT INTO company_job_events(event_id,job_id,kind,created_at) VALUES (?,?,?,CURRENT_TIMESTAMP(6)) "
                + "ON DUPLICATE KEY UPDATE event_id=event_id", event.eventId().toString(), id, event.kind().name());
    }
}
