package kr.itsdev.devjobcollector.company.jobs;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CompanyJobRepository extends JpaRepository<CompanyJob, Long> {
    Page<CompanyJob> findByCompany_IdOrderByIdDesc(Long companyId, Pageable pageable);
    Page<CompanyJob> findByStateOrderByUpdatedAtAsc(CompanyJob.State state, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from CompanyJob j where j.id = :id and j.company.id = :companyId")
    Optional<CompanyJob> lock(@Param("companyId") Long companyId, @Param("id") Long id);
    @Query("select j.company.id from CompanyJob j where j.id = :id")
    Optional<Long> companyId(@Param("id") Long id);
    Optional<CompanyJob> findByPublishedJob_Id(Long id);
    @Query("select count(j) > 0 from CompanyJob j where j.company.id = :companyId and (:excludeId is null or j.id <> :excludeId) and j.state not in :excludedStates and lower(trim(j.content.title)) = lower(trim(:title)) and lower(trim(j.content.applyUrl)) = lower(trim(:url))")
    boolean duplicate(@Param("companyId") Long companyId, @Param("excludeId") Long excludeId,
                      @Param("title") String title, @Param("url") String url,
                      @Param("excludedStates") Collection<CompanyJob.State> excludedStates);
    @Query("select j.id from CompanyJob j where j.publishedJob.endDate < :today and j.publishedJob.moderationStatus = 'ACTIVE'")
    java.util.List<Long> expired(@Param("today") java.time.LocalDate today, Pageable pageable);
}
