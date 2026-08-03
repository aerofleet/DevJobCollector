package kr.itsdev.devjobcollector.collection.repository;

import kr.itsdev.devjobcollector.collection.domain.CompanySourceTarget;
import kr.itsdev.devjobcollector.collection.domain.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanySourceTargetRepository extends JpaRepository<CompanySourceTarget, Long> {
    Optional<CompanySourceTarget> findByProviderAndSourceIdentifier(SourceType provider, String sourceIdentifier);
}
