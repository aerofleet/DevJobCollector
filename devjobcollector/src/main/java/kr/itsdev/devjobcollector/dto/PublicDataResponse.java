package kr.itsdev.devjobcollector.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter @Setter
public class PublicDataResponse {
    private int resultCode;
    private String resultMsg;
    private int totalCount;
    private List<PublicJobDto> result; // 리스트 혹은 단일 객체 대응을 위해 설계
}
