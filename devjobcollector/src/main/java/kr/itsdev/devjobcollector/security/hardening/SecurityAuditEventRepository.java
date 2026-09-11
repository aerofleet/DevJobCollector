package kr.itsdev.devjobcollector.security.hardening;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityAuditEventRepository extends JpaRepository<SecurityAuditEvent, Long> {
    List<SecurityAuditEvent> findAllByCompanyIdOrderByOccurredAtAscIdAsc(Long companyId);
}
