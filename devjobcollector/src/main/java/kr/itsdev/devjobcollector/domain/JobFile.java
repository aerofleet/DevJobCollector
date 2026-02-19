package kr.itsdev.devjobcollector.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "job_files",
    indexes = @Index(name = "idx_post_id", columnList = "post_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_post_id", nullable = false)
    private JobPost jobPost;        //대상 공고와 연결 (FK)

    @Column(name = "file_name",  nullable = false)
    private String fileName;

    @Column(name = "file_url", columnDefinition = "TEXT")
    private String fileUrl;

    @Builder
    public JobFile(
        JobPost jobPost,
        String fileName,
        String fileUrl

        
    ){
        this.jobPost = jobPost;
        this.fileName = fileName;
        this.fileUrl = fileUrl;
    }
}
