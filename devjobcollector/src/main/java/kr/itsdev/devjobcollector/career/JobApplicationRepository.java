package kr.itsdev.devjobcollector.career;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {
    List<JobApplication> findAllByUser_IdOrderByUpdatedAtDescIdDesc(Long userId);
    Optional<JobApplication> findByIdAndUser_Id(Long id, Long userId);
    Optional<JobApplication> findByUser_IdAndJobPost_Id(Long userId, Long jobPostId);
}
