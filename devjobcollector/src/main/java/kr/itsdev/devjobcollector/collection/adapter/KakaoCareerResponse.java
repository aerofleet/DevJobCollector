package kr.itsdev.devjobcollector.collection.adapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record KakaoCareerResponse(
        List<Job> jobList,
        int totalPage,
        int totalJobCount,
        List<JobTypeCount> jobTypeCountDtoList
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Job(
            String realId,
            String jobOfferTitle,
            String companyName,
            String companyNameEn,
            String companyCodeId,
            String introduction,
            String qualification,
            String workContentDesc,
            String jobType,
            String jobTypeName,
            String employeeTypeName,
            String locationName,
            String regDate,
            String uptDate,
            String endDate,
            boolean closeFlag,
            boolean privateFlag,
            String jobPart,
            String jobPartName,
            List<SkillSet> skillSetList
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SkillSet(
            String skillSetType,
            String skillSetName
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record JobTypeCount(
            String jobType,
            String jobTypeName,
            int jobCount
    ) {
    }
}
