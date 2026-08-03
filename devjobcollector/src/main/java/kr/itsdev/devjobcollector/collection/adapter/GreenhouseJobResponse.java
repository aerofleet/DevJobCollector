package kr.itsdev.devjobcollector.collection.adapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record GreenhouseJobResponse(List<Job> jobs) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Job(
            Long id,
            String title,
            String content,
            @JsonProperty("absolute_url") String absoluteUrl,
            @JsonProperty("first_published") String firstPublished,
            @JsonProperty("updated_at") String updatedAt,
            Location location,
            List<Department> departments
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Location(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Department(Long id, String name) {
    }
}
