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
import kr.itsdev.devjobcollector.security.account.UserAccount;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "resumes")
public class CareerResume {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(nullable = false, length = 150)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "resume_status", nullable = false, length = 30)
    private ResumeStatus status = ResumeStatus.DRAFT;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_json", nullable = false, columnDefinition = "json")
    private String contentJson;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected CareerResume() {
    }

    private CareerResume(UserAccount user, String title, String contentJson) {
        this.user = Objects.requireNonNull(user, "user is required");
        this.title = requireText(title, "title is required");
        this.contentJson = requireText(contentJson, "contentJson is required");
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static CareerResume draft(UserAccount user, String title, String contentJson) {
        return new CareerResume(user, title, contentJson);
    }

    public void update(String title, String contentJson) {
        this.title = requireText(title, "title is required");
        this.contentJson = requireText(contentJson, "contentJson is required");
        this.updatedAt = LocalDateTime.now();
    }

    public void changeStatus(ResumeStatus status) {
        this.status = Objects.requireNonNull(status, "status is required");
        this.updatedAt = LocalDateTime.now();
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    public Long getId() { return id; }
    public UserAccount getUser() { return user; }
    public String getTitle() { return title; }
    public ResumeStatus getStatus() { return status; }
    public String getContentJson() { return contentJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
