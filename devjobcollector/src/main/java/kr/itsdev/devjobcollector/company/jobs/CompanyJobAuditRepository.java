package kr.itsdev.devjobcollector.company.jobs;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CompanyJobAuditRepository extends JpaRepository<CompanyJobAudit, Long> {
    List<CompanyJobAudit> findTop50ByCompanyJobIdOrderByIdDesc(Long id);
}
