package kr.itsdev.devjobcollector.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobPostDto {

    private Long id;    
    private String sourcePlatform;
    private String companyName;
    private String title;
    private String jobCategory;
    private String location;
    private String hireType;
    private LocalDate startDate;
    private LocalDate endDate;
    private String originalUrl;
    private boolean isActive;

    // 기술 스택 목록
    private List<String> techStacks;
    
}