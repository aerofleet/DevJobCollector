package kr.itsdev.devjobcollector.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "job_post_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPostImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_post_id", nullable = false)
    private JobPost jobPost;
    @Column(name = "image_url", nullable = false, length = 1000)
    private String imageUrl;
    @Column(name = "position_index", nullable = false)
    private int positionIndex;

    public JobPostImage(JobPost jobPost, String imageUrl, int positionIndex) {
        this.jobPost = jobPost;
        this.imageUrl = imageUrl;
        this.positionIndex = positionIndex;
    }
}
