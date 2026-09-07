package kr.itsdev.devjobcollector.company;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface CompanyMemberRepository extends JpaRepository<CompanyMember, Long> {
    Optional<CompanyMember> findByCompany_IdAndUser_Id(Long companyId, Long userId);
    List<CompanyMember> findAllByUser_IdAndStatusOrderByCompany_IdAsc(
            Long userId, CompanyMemberStatus status);
    boolean existsByCompany_IdAndUser_Id(Long companyId, Long userId);
    long countByCompany_Id(Long companyId);
    long countByCompany_IdAndRoleAndStatus(
            Long companyId, CompanyMemberRole role, CompanyMemberStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT member
            FROM CompanyMember member
            WHERE member.company.id = :companyId AND member.user.id = :userId
            """)
    Optional<CompanyMember> findByCompanyAndUserForUpdate(
            @Param("companyId") Long companyId,
            @Param("userId") Long userId
    );
}
