package kr.itsdev.devjobcollector.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "job_files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class JobFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private JobPost jobPost;        //대상 공고와 연결 (FK)

    @Column(nullable = false)
    private String fileName;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String fileUrl;

}
