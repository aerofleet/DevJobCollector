package kr.itsdev.devjobcollector.admin.jobs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "job_moderation_history")
public class JobModerationHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "job_id", nullable = false)
    private Long jobId;
    @Column(name = "old_status", nullable = false)
    private String oldStatus;
    @Column(name = "new_status", nullable = false)
    private String newStatus;
    @Column(nullable = false)
    private String reason;
    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    protected JobModerationHistory() {}

    public JobModerationHistory(Long jobId, String oldStatus, String newStatus,
                                String reason, Long adminId) {
        this.jobId = jobId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.reason = reason;
        this.adminId = adminId;
    }
}
