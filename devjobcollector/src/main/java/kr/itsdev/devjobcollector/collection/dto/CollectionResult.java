package kr.itsdev.devjobcollector.collection.dto;

import kr.itsdev.devjobcollector.collection.domain.CollectionStatus;

import java.util.List;

public record CollectionResult(
        CollectionStatus status,
        List<JobRawDto> jobs,
        int receivedCount,
        String schemaVersion,
        boolean closureEvaluationAllowed,
        String responseHash,
        java.util.Set<String> explicitlyClosedIds
) {
    public CollectionResult(CollectionStatus status, List<JobRawDto> jobs, int receivedCount,
                            String schemaVersion, boolean closureEvaluationAllowed, String responseHash) {
        this(status, jobs, receivedCount, schemaVersion, closureEvaluationAllowed, responseHash, java.util.Set.of());
    }
    public CollectionResult {
        jobs = jobs == null ? List.of() : List.copyOf(jobs);
        receivedCount = Math.max(receivedCount, 0);
        explicitlyClosedIds = explicitlyClosedIds == null ? java.util.Set.of() : java.util.Set.copyOf(explicitlyClosedIds);
    }

    public static CollectionResult failed(CollectionStatus status, String schemaVersion) {
        return new CollectionResult(status, List.of(), 0, schemaVersion, false, null);
    }
}
