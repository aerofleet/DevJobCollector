package kr.itsdev.devjobcollector.domain;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 기술 스택 마스터 테이블
 * post_tags 중간 테이블을 통해 job_posts와 N:M 관계
 */
@Entity
@Table(
    name = "tech_stacks",
    indexes = @Index(name = "idx_stack_name", columnList = "stack_name")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(exclude = "postTags")
@EqualsAndHashCode(of = "stackName")
public class TechStack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "stack_name", unique = true, nullable = false, length = 50)
    private String stackName;       //기술 명칭 (예: Java, React, MySQL)

    @OneToMany(mappedBy = "techStack", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PostTag> postTags = new ArrayList<>();

    @Builder
    public TechStack(String stackName){
        this.stackName = stackName;
    }

    public void addPostTag(PostTag postTag){
        postTags.add(postTag);
        if (postTag.getTechStack() != this){
            postTag.setTechStack(this);
        }
    }
}
