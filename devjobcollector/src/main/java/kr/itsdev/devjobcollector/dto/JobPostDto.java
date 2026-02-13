package kr.itsdev.devjobcollector.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobPostDto {
    private String title;
    private String companyName;
    private String location;
    private String sourcePlatform;
    // 필요한 필드가 더 있다면 여기에 추가하세요 (startDate, endDate 등)
}