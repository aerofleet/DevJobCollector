package kr.itsdev.devjobcollector.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "job_posts", 
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_source_original",
            columnNames = {"source_platform", "original_sn"}
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(exclude = {"files", "postTags"})
@EqualsAndHashCode(of ={"sourcePlatform", "originalSn"})
public class JobPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_platform", nullable = false, length = 50)
    private SourcePlatform sourcePlatform;

    @Column(name = "original_sn", nullable = false)
    private String originalSn;

    @Column(name = "company_name", nullable = false, length = 150)
    private String companyName;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "job_category")
    private String jobCategory;

    @Column(name = "experience")
    private String experience;

    @Column(name = "experience_category", nullable = false, length = 20)
    private String experienceCategory = "UNKNOWN";

    @Column(name = "experience_label", nullable = false, length = 255)
    private String experienceLabel = "경력 확인 필요";

    @Column(name = "experience_evidence", length = 500)
    private String experienceEvidence;

    @Column(name = "experience_method", nullable = false, length = 20)
    private String experienceMethod = "UNKNOWN";

    @Column(name = "reviewed_experience_category", length = 20)
    private String reviewedExperienceCategory;
    @Column(name = "reviewed_experience_label", length = 255)
    private String reviewedExperienceLabel;
    @Column(name = "reviewed_experience_evidence", length = 1000)
    private String reviewedExperienceEvidence;
    @Column(name = "reviewed_location", length = 255)
    private String reviewedLocation;
    @Column(name = "reviewed_source_hash", length = 64)
    private String reviewedSourceHash;

    @Column(name = "location")
    private String location;
    
    @Column(name = "hire_type")
    private String hireType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "original_posted_date")
    private LocalDate originalPostedDate;
    @Column(name = "complete_description_confirmed", nullable = false)
    private boolean completeDescriptionConfirmed;

    public void updateSearchEvidence(LocalDate postedDate, boolean descriptionConfirmed) {
        this.originalPostedDate = postedDate;
        this.completeDescriptionConfirmed = descriptionConfirmed;
    }

    @Column(name = "source_deadline_display", length = 32)
    private String sourceDeadlineDisplay;

    public void updateSourceDeadlineDisplay(String value) {
        if (value != null && !value.equals("RECRUITMENT_END") && !value.equals("HIDDEN"))
            throw new IllegalArgumentException("Unsupported deadline display value");
        sourceDeadlineDisplay = value;
    }

    @Column(name = "original_url", columnDefinition = "TEXT", nullable = false)
    private String originalUrl;

    @Column(name = "apply_qual", columnDefinition = "LONGTEXT")
    private String applyQual;

    @Column(name = "supplemental_salary_text", columnDefinition = "LONGTEXT")
    private String supplementalSalaryText;
    @Column(name = "supplemental_salary_source_url", columnDefinition = "TEXT")
    private String supplementalSalarySourceUrl;
    @Column(name = "supplemental_salary_source_hash", length = 64)
    private String supplementalSalarySourceHash;
    @Column(name = "supplemental_salary_checked_at")
    private LocalDateTime supplementalSalaryCheckedAt;


    @Column(name = "process_info", columnDefinition = "LONGTEXT")
    private String processInfo;

    @Column(name = "public_preference", columnDefinition = "LONGTEXT")
    private String publicPreference;
    @Column(name = "public_preference_conditions", columnDefinition = "LONGTEXT")
    private String publicPreferenceConditions;
    @Column(name = "public_disqualification", columnDefinition = "LONGTEXT")
    private String publicDisqualification;
    @Column(name = "public_details_checked_at")
    private LocalDateTime publicDetailsCheckedAt;

    public void updatePublicDetails(String preference, String conditions, String disqualification) {
        this.publicPreference = preference;
        this.publicPreferenceConditions = conditions;
        this.publicDisqualification = disqualification;
        this.publicDetailsCheckedAt = LocalDateTime.now();
    }
   
    @Column(name = "is_active", nullable = false, columnDefinition = "BOOLEAN DEFAULT true")
    private boolean isActive = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 30)
    private JobModerationStatus moderationStatus = JobModerationStatus.ACTIVE;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @BatchSize(size = 100)
    @OneToMany(mappedBy = "jobPost", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)    
    private List<JobFile> files = new ArrayList<>();

    @BatchSize(size = 100)
    @OneToMany(mappedBy = "jobPost", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PostTag> postTags = new ArrayList<>();

    @Builder
    public JobPost(
        SourcePlatform sourcePlatform, 
        Boolean isActive,
        String originalSn,
        String companyName,
        String title,
        String jobCategory,
        String experience,
        String location,
        String hireType,
        LocalDate startDate,
        LocalDate endDate,
        String originalUrl,
        String applyQual,
        String processInfo) {
            this.sourcePlatform = sourcePlatform;
            this.originalSn = originalSn;
            this.companyName = companyName;
            this.title = title;
            this.jobCategory = jobCategory;
            this.experience = experience;
            this.location = location;
            this.hireType = hireType;
            this.startDate = startDate;
            this.endDate = endDate;
            this.originalUrl = originalUrl;
            this.applyQual = applyQual;
            this.processInfo = processInfo;            
            this.isActive = true;
            this.postTags = new ArrayList<>();  // ✅ 초기화
            this.files = new ArrayList<>();     // ✅ 초기화
    }

    @PrePersist
    public void prePersist() {
        classifyExperience();
        this.createdAt = LocalDateTime.now();
        if (this.isActive == false) {
            this.isActive = true;
        }
        validateDates();
    }

    @PreUpdate
    public void preUpdate() {
        classifyExperience();
        validateDates();
    }

    private void classifyExperience() {
        if (reviewedSourceHash != null && !reviewedSourceHash.equals(sourceFingerprint())) {
            reviewedExperienceCategory = null;
            reviewedExperienceLabel = null;
            reviewedExperienceEvidence = null;
            reviewedLocation = null;
            reviewedSourceHash = null;
        }
        var classification = ExperienceClassification.classify(experience, title, applyQual, sourcePlatform == SourcePlatform.COMPANY_DIRECT);
        experienceCategory = reviewedExperienceCategory != null ? reviewedExperienceCategory : classification.category();
        experienceLabel = reviewedExperienceLabel != null ? reviewedExperienceLabel : classification.label();
        experienceEvidence = reviewedExperienceEvidence != null ? reviewedExperienceEvidence.substring(0, Math.min(500, reviewedExperienceEvidence.length())) : classification.evidence();
        experienceMethod = reviewedExperienceCategory != null ? "AI_REVIEWED" : classification.method();
    }

    public void applyReviewedMetadata(String category, String label, String evidence, String reviewedLocation) {
        if (category != null) {
            this.reviewedExperienceCategory = category;
            this.reviewedExperienceLabel = label;
            this.reviewedExperienceEvidence = evidence;
        }
        if (reviewedLocation != null) this.reviewedLocation = reviewedLocation;
        this.reviewedSourceHash = sourceFingerprint();
        classifyExperience();
    }

    public String getDisplayLocation() { return reviewedLocation != null ? reviewedLocation : location; }

    private String sourceFingerprint() {
        String source = java.util.Objects.toString(title, "") + "\u0000" + java.util.Objects.toString(experience, "")
                + "\u0000" + java.util.Objects.toString(location, "") + "\u0000" + java.util.Objects.toString(applyQual, "");
        return kr.itsdev.devjobcollector.collection.support.ContentHash.sha256(source);
    }

    private void validateDates() {
        if (endDate != null && startDate != null && endDate.isBefore(startDate)) {
            throw new IllegalStateException("종료일은 시작일보다 이전일 수 없습니다.");
        }
    }

    public void addFile(JobFile file) {
        files.add(file);
        file.setJobPost(this);
    }

    public void removeFile(JobFile file) {
        files.remove(file);
        file.setJobPost(null);
    }

    public void addPostTag(PostTag postTag) {
        if (!postTags.contains(postTag)) {
            postTags.add(postTag);
            if (postTag.getJobPost() != this) {
                postTag.setJobPost(this);
            }
        }
    }

    public void removePostTag(PostTag postTag) {
        postTags.remove(postTag);
        postTag.setJobPost(null);
    }

    /**
     * ✅ 기술스택 추가 - PostTag 생성 및 양방향 연관관계 설정
     */
    public void addTechStack(TechStack techStack) {
         // 중복이 아닌 경우에만 생성
        PostTag postTag = PostTag.builder()
            .jobPost(this)
            .techStack(techStack)
            .build();
    
        this.postTags.add(postTag);
    }

    public void removeTechStack(TechStack techStack) {
        postTags.removeIf(pt -> pt.getTechStack().equals(techStack));
    }

    public List<TechStack> getTechStacks() {
        return postTags.stream()
            .map(PostTag::getTechStack)
            .collect(Collectors.toList());
    }

    public void deactivate() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }

    public void changeModerationStatus(JobModerationStatus moderationStatus) {
        this.moderationStatus = java.util.Objects.requireNonNull(moderationStatus, "moderationStatus is required");
    }

    public void reactivate(LocalDate newEndDate) {
        if (newEndDate != null) {
            if (newEndDate.isBefore(startDate)) {
                throw new IllegalArgumentException("New end date precedes start date");
            }
            this.endDate = newEndDate;
        }
        this.isActive = true;
        this.moderationStatus = JobModerationStatus.ACTIVE;
    }

    public void refreshFromSource(
        String companyName,
        String title,
        String jobCategory,
        String experience,
        String location,
        String hireType,
        LocalDate startDate,
        LocalDate endDate,
        String originalUrl,
        String applyQual,
        String processInfo) {
        this.companyName = companyName;
        this.title = title;
        this.jobCategory = jobCategory;
        this.experience = experience;
        this.location = location;
        this.hireType = hireType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.originalUrl = originalUrl;
        this.applyQual = applyQual;
        this.processInfo = processInfo;
        this.isActive = true;
    }
}
