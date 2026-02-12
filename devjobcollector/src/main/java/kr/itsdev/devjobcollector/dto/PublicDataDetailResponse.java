package kr.itsdev.devjobcollector.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class PublicDataDetailResponse {
    private int resultCode;
    private String resultMsg;
    private int totalCount;

    private PublicJobDto result; // 상세 조회는 List가 아니라 단일 객체(Object)로 온다.

}
