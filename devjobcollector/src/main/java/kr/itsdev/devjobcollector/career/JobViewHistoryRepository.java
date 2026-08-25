package kr.itsdev.devjobcollector.career;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobViewHistoryRepository extends JpaRepository<JobViewHistory, Long> {
    List<JobViewHistory> findAllByUser_IdOrderByLastViewedAtDescIdDesc(Long userId);
    Optional<JobViewHistory> findByUser_IdAndJobPost_Id(Long userId, Long jobPostId);
}
