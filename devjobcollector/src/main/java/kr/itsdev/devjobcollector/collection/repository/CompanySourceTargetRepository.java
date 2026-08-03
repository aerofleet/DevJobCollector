package kr.itsdev.devjobcollector.collection.repository;

import kr.itsdev.devjobcollector.collection.domain.CompanySourceTarget;
import kr.itsdev.devjobcollector.collection.domain.SourceType;
import kr.itsdev.devjobcollector.collection.domain.TargetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CompanySourceTargetRepository extends JpaRepository<CompanySourceTarget, Long> {
    Optional<CompanySourceTarget> findByProviderAndSourceIdentifier(SourceType provider, String sourceIdentifier);

    @Query("SELECT t FROM CompanySourceTarget t " +
            "WHERE t.status IN :statuses " +
            "AND (t.nextCollectAt IS NULL OR t.nextCollectAt <= :now) " +
            "ORDER BY t.nextCollectAt ASC, t.id ASC")
    List<CompanySourceTarget> findDueTargets(
            @Param("statuses") Collection<TargetStatus> statuses,
            @Param("now") Instant now);
}
