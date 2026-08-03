package kr.itsdev.devjobcollector.collection.dto;

import kr.itsdev.devjobcollector.collection.domain.CollectionStatus;

import java.util.List;

public record CollectionResult(
        CollectionStatus status,
        List<JobRawDto> jobs,
        int receivedCount,
        String schemaVersion,
        boolean closureEvaluationAllowed,
        String responseHash
) {
    public CollectionResult {
        jobs = jobs == null ? List.of() : List.copyOf(jobs);
        receivedCount = Math.max(receivedCount, 0);
    }

    public static CollectionResult failed(CollectionStatus status, String schemaVersion) {
        return new CollectionResult(status, List.of(), 0, schemaVersion, false, null);
    }
}
