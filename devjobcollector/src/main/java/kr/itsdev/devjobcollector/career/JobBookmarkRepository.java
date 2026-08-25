package kr.itsdev.devjobcollector.career;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobBookmarkRepository extends JpaRepository<JobBookmark, Long> {
    List<JobBookmark> findAllByUser_IdOrderByCreatedAtDescIdDesc(Long userId);
    boolean existsByUser_IdAndJobPost_Id(Long userId, Long jobPostId);
    long deleteByUser_IdAndJobPost_Id(Long userId, Long jobPostId);
}
