package kr.itsdev.devjobcollector.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@Getter @Setter
@JsonIgnoreProperties(ignoreUnknown = true) // JSON에 있는 필드가 DTO에 없어도 무시하여 에러 방지
public class PublicJobDto {

    @JsonProperty("recrutPblntSn")
    private String recrutPblntSn;      // 공공 일련번호

    @JsonProperty("instNm")
    private String instNm;              // 기관명

    @JsonProperty("recrutPbancTtl")
    private String recrutPbancTtl;      // 공고 제목

    @JsonProperty("ncsCdNmLst")
    private String ncsCdNmLst;          // 직무 분야

    @JsonProperty("workRgnNmLst")
    private String workRgnNmLst;        // 근무 지역

    @JsonProperty("hireTypeNmLst")
    private String hireTypeNmLst;       // 고용 형태

    @JsonProperty("recrutSeNm")
    private String recrutSeNm;          // 채용 구분 (신입/경력)

    @JsonProperty("pbancBgngYmd")
    private String pbancBgngYmd;        // 시작일

    @JsonProperty("pbancEndYmd")
    private String pbancEndYmd;         // 마감일

    @JsonProperty("srcUrl")
    private String srcUrl;              // 원본 URL

    @JsonProperty("aplyQlfcCn")
    private String aplyQlfcCn;          // 상세 지원 자격 (Detail API용)

    @JsonProperty("scrnprcdrMthdExpln")
    private String scrnprcdrMthdExpln;  // 전형 방법 (Detail API용)

    @JsonProperty("files")
    private List<FileDto> files;        // 첨부파일 목록

    @Getter @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FileDto {
        @JsonProperty("atchFileNm")
        private String atchFileNm;      // 파일명

        @JsonProperty("url")
        private String url;             // 다운로드 주소
    }
}
