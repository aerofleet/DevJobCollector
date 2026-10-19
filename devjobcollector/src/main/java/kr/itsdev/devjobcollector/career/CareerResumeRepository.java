package kr.itsdev.devjobcollector.career;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareerResumeRepository extends JpaRepository<CareerResume, Long> {
    @org.springframework.data.jpa.repository.Query("""
            SELECT r FROM CareerResume r
            WHERE r.visibility = kr.itsdev.devjobcollector.career.ResumeVisibility.PUBLIC
               OR EXISTS (SELECT a.id FROM JobApplication a, CompanyJob j
                          WHERE a.user.id = r.user.id AND a.jobPost.id = j.publishedJob.id
                            AND j.company.id = :companyId
                            AND a.status <> kr.itsdev.devjobcollector.career.ApplicationStatus.WITHDRAWN)
            """)
    org.springframework.data.domain.Page<CareerResume> findVisibleToCompany(
            @org.springframework.data.repository.query.Param("companyId") Long companyId,
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("""
            SELECT r FROM CareerResume r WHERE r.id = :resumeId AND (
                r.visibility = kr.itsdev.devjobcollector.career.ResumeVisibility.PUBLIC
                OR EXISTS (SELECT a.id FROM JobApplication a, CompanyJob j
                           WHERE a.user.id = r.user.id AND a.jobPost.id = j.publishedJob.id
                             AND j.company.id = :companyId
                             AND a.status <> kr.itsdev.devjobcollector.career.ApplicationStatus.WITHDRAWN))
            """)
    Optional<CareerResume> findVisibleToCompanyById(
            @org.springframework.data.repository.query.Param("companyId") Long companyId,
            @org.springframework.data.repository.query.Param("resumeId") Long resumeId);
    List<CareerResume> findAllByUser_IdOrderByUpdatedAtDescIdDesc(Long userId);
    Optional<CareerResume> findByIdAndUser_Id(Long id, Long userId);
}
