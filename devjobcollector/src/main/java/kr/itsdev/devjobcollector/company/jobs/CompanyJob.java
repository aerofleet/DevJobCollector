package kr.itsdev.devjobcollector.company.jobs;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import kr.itsdev.devjobcollector.company.Company;
import kr.itsdev.devjobcollector.domain.JobPost;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "company_jobs")
@Getter
@NoArgsConstructor
public class CompanyJob {
    public enum State { DRAFT, IN_REVIEW, CHANGES_REQUESTED, PUBLISHED, CLOSED, SUSPENDED, DELETED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name="company_id") private Company company;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name="published_job_id", unique=true) private JobPost publishedJob;
    @Embedded private CompanyJobContent content;
    @Enumerated(EnumType.STRING) @Column(length=30, nullable=false) private State state = State.DRAFT;
    @Column(length=500) private String reviewReason;
    @Version private long version;
    @Column(nullable=false) private LocalDateTime updatedAt;

    public CompanyJob(Company company, CompanyJobContent content) {
        this.company = company; this.content = content; touch();
    }
    public void edit(CompanyJobContent content) { this.content = content; state = State.DRAFT; reviewReason = null; touch(); }
    public void transition(State state, String reason) { this.state = state; this.reviewReason = reason; touch(); }
    public void publish(JobPost post) { publishedJob = post; transition(State.PUBLISHED, null); }
    private void touch() { updatedAt = LocalDateTime.now(); }
}
