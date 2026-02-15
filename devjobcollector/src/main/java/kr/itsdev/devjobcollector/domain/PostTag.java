package kr.itsdev.devjobcollector.domain;

import java.io.Serializable;

import jakarta.persistence.*;
import lombok.*;

/**
 * job_post와 tech_stacks의 중간 테이블 (N:M 관계)
 */
@Entity
@Table(name = "post_tags",
    indexes = {
        @Index(name = "idx_post_id", columnList = "post_id"),
        @Index(name = "idx_stack_id", columnList = "stack_id")
    }
)
@IdClass(PostTag.PostTagId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(exclude = {"jobPost", "techStack"})
@EqualsAndHashCode(of = {"jobPost", "techStack"})
public class PostTag {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private JobPost jobPost;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stack_id", nullable = false)
    private TechStack techStack;

    @Builder
    public PostTag(JobPost jobPost, TechStack techStack) {
        this.jobPost = jobPost;
        this.techStack = techStack;
    }

    public static PostTag of(JobPost jobPost, TechStack techStack) {
        PostTag postTag = PostTag.builder()
            .jobPost(jobPost)
            .techStack(techStack)
            .build();
        
        jobPost.addPostTag(postTag);
        techStack.addPostTag(postTag);
        
        return postTag;
    }

    void setJobPost(JobPost jobPost) {
        this.jobPost = jobPost;
    }

    void setTechStack(TechStack techStack) {
        this.techStack = techStack;
    }

    /**
     * 복합 키 클래스
     */
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class PostTagId implements Serializable {
        private Long jobPost;      // JobPost의 id 타입
        private Integer techStack; // TechStack의 id 타입
    }
}
