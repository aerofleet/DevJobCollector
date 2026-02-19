package kr.itsdev.devjobcollector.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TechStackDto {
    private Integer id;
    private String stackName;
}
