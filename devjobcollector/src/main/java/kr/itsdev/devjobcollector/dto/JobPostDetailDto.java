package kr.itsdev.devjobcollector.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 채용 공고 상세 조회용 DTO
 */

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobPostDetailDto {

    private Long id;
    private String sourcePlatform;
    private String companyName;
    private String title;
    private String jobCategory;
    private String experience;
    private String location;
    private String hireType;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime createdAt;
    private String originalUrl;

    // 상세 정보
    private String applyQual;
    private String processInfo;

    private boolean isActive;

    // 연관 데이터
    private List<TechStackDto> techStacks;
    private List<JobFileDto> files;

}
