package kr.itsdev.devjobcollector.collection.adapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record NaverCareerResponse(
        Integer totalSize,
        List<Job> list
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Job(
            Long annoId,
            String sysCompanyCdNm,
            String annoSubject,
            String entTypeCdNm,
            String workAreaCd,
            String staYmdTime,
            String endYmdTime,
            String classCdNm,
            String subJobCdNm,
            String empTypeCdNm,
            String jobDetailLink
    ) {
    }
}
