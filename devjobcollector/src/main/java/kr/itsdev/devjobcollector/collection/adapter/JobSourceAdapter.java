package kr.itsdev.devjobcollector.collection.adapter;

import kr.itsdev.devjobcollector.collection.domain.CompanySourceTarget;
import kr.itsdev.devjobcollector.collection.domain.SourceType;
import kr.itsdev.devjobcollector.collection.dto.CollectionContext;
import kr.itsdev.devjobcollector.collection.dto.CollectionResult;

public interface JobSourceAdapter {

    SourceType sourceType();

    CollectionResult fetchJobs(CompanySourceTarget target, CollectionContext context);

    default boolean supportsExplicitClosedStatus() {
        return false;
    }
}
