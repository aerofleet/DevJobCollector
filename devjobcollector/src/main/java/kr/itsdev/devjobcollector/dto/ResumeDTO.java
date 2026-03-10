package kr.itsdev.devjobcollector.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeDTO {
    private Long id;
    private BasicInfo basicInfo;
    private List<TechStack> techStack;
    private List<Project> projects;
    private List<Experience> experience;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BasicInfo {
        private String name;
        private String email;
        private String phone;
        private LocalDate birthDate;
        private String address;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TechStack {
        private String name;
        private String category;
        private Integer level;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Project {
        private String title;
        private String description;
        private LocalDate startDate;
        private LocalDate endDate;
        private List<String> techs;
        private String link;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Experience {
        private String company;
        private String position;
        private LocalDate startDate;
        private LocalDate endDate;
        private String description;
    }
}
