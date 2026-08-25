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
@Table(name = "job_bookmarks")
public class JobBookmark {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_post_id", nullable = false)
    private JobPost jobPost;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected JobBookmark() {
    }

    private JobBookmark(UserAccount user, JobPost jobPost) {
        this.user = Objects.requireNonNull(user, "user is required");
        this.jobPost = Objects.requireNonNull(jobPost, "jobPost is required");
    }

    public static JobBookmark create(UserAccount user, JobPost jobPost) {
        return new JobBookmark(user, jobPost);
    }

    public Long getId() { return id; }
    public UserAccount getUser() { return user; }
    public JobPost getJobPost() { return jobPost; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
