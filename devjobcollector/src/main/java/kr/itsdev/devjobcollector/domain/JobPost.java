package kr.itsdev.devjobcollector.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 채용 공고 메인 엔티티
 * 복합 유니크 키(source_platform, original_sn)를 통해 데이터 중복 수집을 방지한다.
 */
@Entity
@Table(name = "job_posts", uniqueConstraints = {
    @UniqueConstraint(
        name = "uk_source_original",
        columnNames = {"source_platform", "original_sn"}
    )
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor

public class JobPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_platform", nullable = false, length = 50)
    private String sourcePlatform;     //수집 출처 (예: PUBLIC_ALTO, SARAMIN)

    @Column(name = "original_sn", nullable = false)
    private String originalSn;         //플렛폼별 고유 번호

    @Column(name = "company_name", nullable = false, length = 150)
    private String companyName;

    @Column(nullable = false, length = 255)
    private String title;

    private String jobCategory;
    private String location;
    private String hireType;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String originalUrl;

    @Column(columnDefinition = "LONGTEXT")
    private String applyQual;           //상세 지원 자격 (Detail API 수집 데이터)

    @Column(columnDefinition = "LONGTEXT")
    private String processInfo;         //전형 방법 실시

    @Builder.Default
    @Column(nullable = false)
    private boolean isActive = true;    //Soft Delete용 필드

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "jobPost", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<JobFile> files = new ArrayList<>();

    @ManyToMany
    @JoinTable(
        name = "post_tags",
        joinColumns = @JoinColumn(name = "post_id"),
        inverseJoinColumns = @JoinColumn(name = "stack_id")
    )
    @Builder.Default
    private List<TechStack> techStacks = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
