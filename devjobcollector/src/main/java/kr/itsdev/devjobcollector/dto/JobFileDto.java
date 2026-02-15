package kr.itsdev.devjobcollector.dto;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobFileDto {
    private Long id;
    private String fileName;
    private String fileUrl;
}
