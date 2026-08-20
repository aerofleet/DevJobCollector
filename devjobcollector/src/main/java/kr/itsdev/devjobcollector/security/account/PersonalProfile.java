package kr.itsdev.devjobcollector.security.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "personal_profiles")
public class PersonalProfile {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_status", nullable = false, length = 30)
    private ProfileStatus profileStatus = ProfileStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected PersonalProfile() {
    }

    private PersonalProfile(UserAccount user) {
        this.user = Objects.requireNonNull(user, "user is required");
    }

    public static PersonalProfile active(UserAccount user) {
        return new PersonalProfile(user);
    }

    public void changeStatus(ProfileStatus profileStatus) {
        this.profileStatus = Objects.requireNonNull(profileStatus, "profileStatus is required");
    }

    public Long getUserId() { return userId; }
    public UserAccount getUser() { return user; }
    public ProfileStatus getProfileStatus() { return profileStatus; }
}
