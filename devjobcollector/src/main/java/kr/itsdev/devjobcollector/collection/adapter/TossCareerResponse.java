package kr.itsdev.devjobcollector.collection.adapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record TossCareerResponse(List<JobGroup> success) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record JobGroup(
            Long id,
            String title,
            @JsonProperty("primary_job") Job primaryJob,
            List<Job> jobs
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Job(
            Long id,
            String title,
            @JsonProperty("absolute_url") String absoluteUrl,
            @JsonProperty("internal_job_id") Long internalJobId,
            @JsonProperty("updated_at") String updatedAt,
            @JsonProperty("company_name") String companyName,
            @JsonProperty("first_published") String firstPublished,
            @JsonProperty("application_deadline") String applicationDeadline,
            Location location,
            List<Metadata> metadata
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Location(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Metadata(Long id, String name, JsonNode value) {
    }
}
