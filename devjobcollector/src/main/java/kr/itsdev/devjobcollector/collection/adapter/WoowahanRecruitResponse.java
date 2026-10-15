package kr.itsdev.devjobcollector.collection.adapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record WoowahanRecruitResponse(
        String code,
        String message,
        Data data
) {
    boolean isSuccess() {
        return "2000".equals(code) && data != null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Data(
            int pageSize,
            int pageNumber,
            int totalPageNumber,
            int totalSize,
            List<Recruit> list
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Recruit(
            long recruitSeq,
            String recruitNumber,
            String recruitName,
            String recruitCorporationNumber,
            String recruitOpenDate,
            String recruitEndDate,
            int careerRestrictionMinYears,
            int careerRestrictionMaxYears,
            String recruitContents,
            CodeItem careerType,
            CodeItem jobGroup,
            CodeItem employmentType
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CodeItem(
            String recruitItemGroupCode,
            String recruitItemCode
    ) {
    }
}
