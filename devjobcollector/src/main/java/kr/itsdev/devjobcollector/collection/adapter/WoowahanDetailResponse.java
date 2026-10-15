package kr.itsdev.devjobcollector.collection.adapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record WoowahanDetailResponse(
        String code,
        String message,
        WoowahanRecruitResponse.Recruit data
) {
    boolean isSuccess() {
        return "2000".equals(code) && data != null;
    }
}
