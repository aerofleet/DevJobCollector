package kr.itsdev.devjobcollector.company;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    boolean existsByBusinessNumberHash(String businessNumberHash);

    @Query("SELECT c FROM Company c WHERE (:status IS NULL OR c.status = :status) "
            + "AND (:keyword IS NULL OR LOWER(c.displayName) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(c.legalName) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Company> searchForAdmin(@Param("keyword") String keyword,
                                 @Param("status") CompanyStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT company FROM Company company WHERE company.id = :companyId")
    Optional<Company> findByIdForUpdate(@Param("companyId") Long companyId);
}
