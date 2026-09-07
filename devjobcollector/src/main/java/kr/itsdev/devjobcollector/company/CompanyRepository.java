package kr.itsdev.devjobcollector.company;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    boolean existsByBusinessNumberHash(String businessNumberHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT company FROM Company company WHERE company.id = :companyId")
    Optional<Company> findByIdForUpdate(@Param("companyId") Long companyId);
}
