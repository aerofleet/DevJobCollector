package kr.itsdev.devjobcollector.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * JobPost와 TechStack의 중간 테이블 엔티티
 */
@Entity
@Table(name = "post_tags",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_post_tech",
        columnNames = {"job_post_id", "tech_stack_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(exclude = {"jobPost", "techStack"})
@EqualsAndHashCode(of = {"jobPost", "techStack"})
public class PostTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_post_id", nullable = false)
    private JobPost jobPost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tech_stack_id", nullable = false)
    private TechStack techStack;

    @Builder
    private PostTag(JobPost jobPost, TechStack techStack) {
        this.jobPost = jobPost;
        this.techStack = techStack;
    }

    /**
     * ✅ 정적 팩토리 메서드 - 양방향 연관관계 설정
     */
    public static PostTag of(JobPost jobPost, TechStack techStack) {
        PostTag postTag = PostTag.builder()
                .jobPost(jobPost)
                .techStack(techStack)
                .build();
        
        // 양방향 연관관계 설정
        jobPost.getPostTags().add(postTag);
        
        return postTag;
    }
}