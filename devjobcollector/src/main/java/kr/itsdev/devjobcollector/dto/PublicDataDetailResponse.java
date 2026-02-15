package kr.itsdev.devjobcollector.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PublicDataDetailResponse {

    @JsonProperty("resultCode")
    private Integer resultCode;

    @JsonProperty("resultMsg")
    private String resultMsg;

    @JsonProperty("totalCount")
    private int totalCount;

    @JsonProperty("result")
    private PublicJobDto result;

    public boolean isSuccess(){
        return resultCode != null && resultCode == 200;
    }

    public boolean hasDate(){
        return isSuccess() && result != null;
    }

}
