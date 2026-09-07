package kr.itsdev.devjobcollector.company;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyVerificationRequestRepository
        extends JpaRepository<CompanyVerificationRequest, Long> {
    boolean existsByCompany_IdAndStatus(Long companyId, CompanyVerificationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT request FROM CompanyVerificationRequest request WHERE request.id = :requestId")
    Optional<CompanyVerificationRequest> findByIdForUpdate(@Param("requestId") Long requestId);
}
