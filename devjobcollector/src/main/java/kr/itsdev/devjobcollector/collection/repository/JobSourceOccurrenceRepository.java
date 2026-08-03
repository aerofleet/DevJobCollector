package kr.itsdev.devjobcollector.collection.repository;

import kr.itsdev.devjobcollector.collection.domain.JobSourceOccurrence;
import kr.itsdev.devjobcollector.collection.domain.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobSourceOccurrenceRepository extends JpaRepository<JobSourceOccurrence, Long> {
    Optional<JobSourceOccurrence> findByProviderAndTargetIdAndSourceJobId(
            SourceType provider, Long targetId, String sourceJobId);
}
