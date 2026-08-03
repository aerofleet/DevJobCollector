package kr.itsdev.devjobcollector.collection.repository;

import kr.itsdev.devjobcollector.collection.domain.JobRawSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRawSnapshotRepository extends JpaRepository<JobRawSnapshot, Long> {
}
