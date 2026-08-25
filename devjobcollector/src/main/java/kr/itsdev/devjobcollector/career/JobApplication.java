package kr.itsdev.devjobcollector.career;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "applications")
public class JobApplication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_post_id", nullable = false)
    private JobPost jobPost;

    @Enumerated(EnumType.STRING)
    @Column(name = "application_status", nullable = false, length = 30)
    private ApplicationStatus status = ApplicationStatus.APPLIED;

    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;

    @Column(length = 1000)
    private String memo;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected JobApplication() {
    }

    private JobApplication(UserAccount user, JobPost jobPost, LocalDateTime appliedAt, String memo) {
        this.user = Objects.requireNonNull(user, "user is required");
        this.jobPost = Objects.requireNonNull(jobPost, "jobPost is required");
        this.appliedAt = Objects.requireNonNull(appliedAt, "appliedAt is required");
        this.memo = normalizeMemo(memo);
    }

    public static JobApplication applied(
            UserAccount user, JobPost jobPost, LocalDateTime appliedAt, String memo
    ) {
        return new JobApplication(user, jobPost, appliedAt, memo);
    }

    public void changeStatus(ApplicationStatus status) {
        this.status = Objects.requireNonNull(status, "status is required");
    }

    public void updateMemo(String memo) {
        this.memo = normalizeMemo(memo);
    }

    private static String normalizeMemo(String memo) {
        if (memo == null || memo.isBlank()) {
            return null;
        }
        String normalized = memo.trim();
        if (normalized.length() > 1000) {
            throw new IllegalArgumentException("memo must not exceed 1000 characters");
        }
        return normalized;
    }

    public Long getId() { return id; }
    public UserAccount getUser() { return user; }
    public JobPost getJobPost() { return jobPost; }
    public ApplicationStatus getStatus() { return status; }
    public LocalDateTime getAppliedAt() { return appliedAt; }
    public String getMemo() { return memo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
