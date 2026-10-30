package kr.itsdev.devjobcollector.collection.adapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
record LeverPostingResponse(
        String id,
        String text,
        String hostedUrl,
        String applyUrl,
        String descriptionPlain,
        String additionalPlain,
        String salaryDescriptionPlain,
        SalaryRange salaryRange,
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SalaryRange(BigDecimal min, BigDecimal max, String currency, String interval) { }
}
