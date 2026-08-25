package kr.itsdev.devjobcollector.career;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareerResumeRepository extends JpaRepository<CareerResume, Long> {
    List<CareerResume> findAllByUser_IdOrderByUpdatedAtDescIdDesc(Long userId);
    Optional<CareerResume> findByIdAndUser_Id(Long id, Long userId);
}
