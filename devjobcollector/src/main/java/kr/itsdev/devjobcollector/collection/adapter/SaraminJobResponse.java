package kr.itsdev.devjobcollector.collection.adapter;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record SaraminJobResponse(Jobs jobs, Result result) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Jobs(Integer count, Integer start, Long total,
                @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) List<Job> job) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Result(String code, String message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Job(
            String id,
            String url,
            Integer active,
            Company company,
            Position position,
            @JsonProperty("posting-timestamp") Long postingTimestamp,
            @JsonProperty("modification-timestamp") Long modificationTimestamp,
            @JsonProperty("expiration-timestamp") Long expirationTimestamp,
            @JsonProperty("close-type") NamedValue closeType
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Company(CompanyDetail detail) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CompanyDetail(String href, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Position(
            String title,
            NamedValue location,
            @JsonProperty("job-type") NamedValue jobType,
            @JsonProperty("job-mid-code") NamedValue jobMidCode,
            @JsonProperty("experience-level") NamedValue experienceLevel
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NamedValue(String code, String name) {
    }
}
