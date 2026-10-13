package kr.itsdev.devjobcollector.company.jobs;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface CompanyJobAuditRepository extends JpaRepository<CompanyJobAudit, Long> {
    List<CompanyJobAudit> findTop50ByCompanyJobIdOrderByIdDesc(Long id);
    @Query("select a from CompanyJobAudit a where (:action is null or a.action = :action) "
            + "and (:jobId is null or a.companyJobId = :jobId) order by a.id desc")
    Page<CompanyJobAudit> history(@Param("action") String action, @Param("jobId") Long jobId, Pageable pageable);
}
