package kr.itsdev.devjobcollector.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

/**
 * 공공데이터 포털 채용 공고 목록 조회 API 응답 DTO
 * GET /getJobList
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PublicDataListResponse {
    
    @JsonProperty("resultCode")
    private Integer resultCode;
    
    @JsonProperty("resultMsg")
    private String resultMsg;
    
    @JsonProperty("totalCount")
    private Integer totalCount;
    
    /**
     * 공고 목록 (배열)
     */
    @JsonProperty("result")
    private List<PublicJobDto> result;
    
    /**
     * API 호출 성공 여부
     */
    public boolean isSuccess() {
        return resultCode != null && resultCode == 200;
    }
    
    /**
     * 데이터 존재 여부
     */
    public boolean hasData() {
        return isSuccess() && result != null && !result.isEmpty();
    }
    
    /**
     * 결과 개수 반환
     */
    public int getResultSize() {
        return result != null ? result.size() : 0;
    }
}