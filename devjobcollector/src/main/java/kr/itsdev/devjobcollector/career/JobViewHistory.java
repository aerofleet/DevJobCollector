package kr.itsdev.devjobcollector.career;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.security.account.UserAccount;

@Entity
@Table(name = "job_view_history")
public class JobViewHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_post_id", nullable = false)
    private JobPost jobPost;

    @Column(name = "first_viewed_at", nullable = false)
    private LocalDateTime firstViewedAt;

    @Column(name = "last_viewed_at", nullable = false)
    private LocalDateTime lastViewedAt;

    @Column(name = "view_count", nullable = false)
    private int viewCount = 1;

    protected JobViewHistory() {
    }

    private JobViewHistory(UserAccount user, JobPost jobPost, LocalDateTime viewedAt) {
        this.user = Objects.requireNonNull(user, "user is required");
        this.jobPost = Objects.requireNonNull(jobPost, "jobPost is required");
        this.firstViewedAt = Objects.requireNonNull(viewedAt, "viewedAt is required");
        this.lastViewedAt = viewedAt;
    }

    public static JobViewHistory firstView(UserAccount user, JobPost jobPost, LocalDateTime viewedAt) {
        return new JobViewHistory(user, jobPost, viewedAt);
    }

    public void recordView(LocalDateTime viewedAt) {
        LocalDateTime nextViewedAt = Objects.requireNonNull(viewedAt, "viewedAt is required");
        if (nextViewedAt.isBefore(lastViewedAt)) {
            throw new IllegalArgumentException("viewedAt cannot precede lastViewedAt");
        }
        this.lastViewedAt = nextViewedAt;
        this.viewCount++;
    }

    public Long getId() { return id; }
    public UserAccount getUser() { return user; }
    public JobPost getJobPost() { return jobPost; }
    public LocalDateTime getFirstViewedAt() { return firstViewedAt; }
    public LocalDateTime getLastViewedAt() { return lastViewedAt; }
    public int getViewCount() { return viewCount; }
}
