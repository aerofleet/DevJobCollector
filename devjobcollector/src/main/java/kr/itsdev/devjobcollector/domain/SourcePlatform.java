package kr.itsdev.devjobcollector.domain;

import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;

@Getter
public enum SourcePlatform {
    PUBLIC_ALIO("공공기관 알리오"),
    SARAMIN("사람인"),
    JOBKOREA("잡코리아");

    private final String platFormName;

    SourcePlatform(String platFormName) {
        this.platFormName = platFormName;
    }

    @JsonValue // 이 어노테이션이 있으면 API 응답 시 platFormName 값이 나감
    public String getPlatFormName() {
        return platFormName;
    }
}
