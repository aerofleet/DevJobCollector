package kr.itsdev.devjobcollector.company;

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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;
import kr.itsdev.devjobcollector.security.account.UserAccount;

@Entity
@Table(name = "company_members", uniqueConstraints = @UniqueConstraint(
        name = "uk_company_members_company_user", columnNames = {"company_id", "user_id"}))
public class CompanyMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CompanyMemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CompanyMemberStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by", updatable = false)
    private UserAccount invitedBy;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected CompanyMember() {
    }

    private CompanyMember(Company company, UserAccount user, CompanyMemberRole role,
                          CompanyMemberStatus status, UserAccount invitedBy, LocalDateTime joinedAt) {
        this.company = Objects.requireNonNull(company, "company is required");
        this.user = Objects.requireNonNull(user, "user is required");
        this.role = Objects.requireNonNull(role, "role is required");
        this.status = Objects.requireNonNull(status, "status is required");
        this.invitedBy = invitedBy;
        this.joinedAt = joinedAt;
        if (status == CompanyMemberStatus.ACTIVE && joinedAt == null) {
            throw new IllegalArgumentException("joinedAt is required for an active member");
        }
    }

    public static CompanyMember activeOwner(Company company, UserAccount user, LocalDateTime joinedAt) {
        return new CompanyMember(company, user, CompanyMemberRole.OWNER,
                CompanyMemberStatus.ACTIVE, null,
                Objects.requireNonNull(joinedAt, "joinedAt is required"));
    }

    public static CompanyMember invited(Company company, UserAccount user, CompanyMemberRole role,
                                        UserAccount invitedBy) {
        return new CompanyMember(company, user, role, CompanyMemberStatus.INVITED,
                Objects.requireNonNull(invitedBy, "invitedBy is required"), null);
    }

    void changeRole(CompanyMemberRole role) {
        this.role = Objects.requireNonNull(role, "role is required");
    }

    void changeStatus(CompanyMemberStatus status, LocalDateTime occurredAt) {
        this.status = Objects.requireNonNull(status, "status is required");
        if (status == CompanyMemberStatus.ACTIVE && joinedAt == null) {
            this.joinedAt = Objects.requireNonNull(occurredAt, "occurredAt is required when activating a member");
        }
    }

    public boolean isActiveOwner() {
        return role == CompanyMemberRole.OWNER && status == CompanyMemberStatus.ACTIVE;
    }

    public Long getId() { return id; }
    public Company getCompany() { return company; }
    public UserAccount getUser() { return user; }
    public CompanyMemberRole getRole() { return role; }
    public CompanyMemberStatus getStatus() { return status; }
    public UserAccount getInvitedBy() { return invitedBy; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
