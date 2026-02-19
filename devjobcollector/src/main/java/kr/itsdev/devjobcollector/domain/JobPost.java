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

    @Column(name = "location")
    private String location;
    
    @Column(name = "hire_type")
    private String hireType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "original_url", columnDefinition = "TEXT", nullable = false)
    private String originalUrl;

    @Column(name = "apply_qual", columnDefinition = "LONGTEXT")
    private String applyQual;

    @Column(name = "process_info", columnDefinition = "LONGTEXT")
    private String processInfo;
   
    @Column(name = "is_active", nullable = false, columnDefinition = "BOOLEAN DEFAULT true")
    private boolean isActive = true;

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
        this.createdAt = LocalDateTime.now();
        if (this.isActive == false) {
            this.isActive = true;
        }
        validateDates();
    }

    @PreUpdate
    public void preUpdate() {
        validateDates();
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
        if (postTags.stream().noneMatch(pt -> pt.getTechStack().equals(techStack))) {
            PostTag.of(this, techStack);
        }
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
}