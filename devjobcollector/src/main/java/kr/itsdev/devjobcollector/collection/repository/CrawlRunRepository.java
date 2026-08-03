package kr.itsdev.devjobcollector.collection.repository;

import kr.itsdev.devjobcollector.collection.domain.CollectionStatus;
import kr.itsdev.devjobcollector.collection.domain.CrawlRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CrawlRunRepository extends JpaRepository<CrawlRun, Long> {
    Optional<CrawlRun> findTopByTargetIdAndStatusOrderByStartedAtDesc(Long targetId, CollectionStatus status);
}
