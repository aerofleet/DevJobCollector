package kr.itsdev.devjobcollector.collection.adapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record LeverPostingResponse(
        String id,
        String text,
        String hostedUrl,
        String applyUrl,
        String descriptionPlain,
        Long createdAt,
        String workplaceType,
        Categories categories,
        List<ContentList> lists
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Categories(String commitment, String location, String team, List<String> allLocations) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContentList(String text, String content) {
    }
}
