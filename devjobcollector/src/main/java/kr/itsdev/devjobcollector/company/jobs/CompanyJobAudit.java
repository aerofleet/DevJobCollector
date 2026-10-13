package kr.itsdev.devjobcollector.company.jobs;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name="company_job_audits") @Getter @NoArgsConstructor
public class CompanyJobAudit {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long companyJobId;
    private Long actorId;
    @Column(nullable=false,length=20) private String actorType;
    @Column(nullable=false,length=40) private String action;
    @Column(length=500) private String reason;
    @Column(nullable=false) private LocalDateTime createdAt;
    public CompanyJobAudit(Long jobId, Long actor, String type, String action, String reason) {
        companyJobId=jobId; actorId=actor; actorType=type; this.action=action;
        this.reason=reason; createdAt=LocalDateTime.now();
    }
}
