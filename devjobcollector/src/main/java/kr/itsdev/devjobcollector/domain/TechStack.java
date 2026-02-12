package kr.itsdev.devjobcollector.domain;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "teck_stacks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TechStack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(unique = true, nullable = false, length = 50)
    private String stackName;       //기술 명칭 (예: Java, React, MySQL)

    @ManyToMany(mappedBy = "techStacks")
    @Builder.Default
    private List<JobPost> jobPosts = new ArrayList<>();

}
