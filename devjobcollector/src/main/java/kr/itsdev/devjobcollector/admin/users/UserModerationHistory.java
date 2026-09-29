package kr.itsdev.devjobcollector.admin.users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_moderation_history")
public class UserModerationHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "old_status", nullable = false)
    private String oldStatus;
    @Column(name = "new_status", nullable = false)
    private String newStatus;
    @Column(nullable = false)
    private String reason;
    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    protected UserModerationHistory() {}

    public UserModerationHistory(Long userId, String oldStatus, String newStatus,
                                 String reason, Long adminId) {
        this.userId = userId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.reason = reason;
        this.adminId = adminId;
    }
}
