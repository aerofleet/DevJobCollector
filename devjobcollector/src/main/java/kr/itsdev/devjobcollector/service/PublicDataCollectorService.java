package kr.itsdev.devjobcollector.service;

import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.domain.SourcePlatform;
import kr.itsdev.devjobcollector.dto.PublicDataDetailResponse;
import kr.itsdev.devjobcollector.dto.PublicDataResponse;
import kr.itsdev.devjobcollector.dto.PublicJobDto;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublicDataCollectorService {

    private final JobPostRepository jobPostRepository;
    private final PublicDataApiClient apiClient;

    @Transactional
    public void processAndSave(PublicJobDto dto) {

        //중복 체크: 플랫폼과 고유 번호 조합으로 이미 DB에 있는지 확인
        if (jobPostRepository.existsBySourcePlatformAndOriginalSn("PUBLIC_ALIO", dto.getRecrutPblntSn())) {
            log.info("이미 존재하는 공고입니다. Skip: {}", dto.getRecrutPblntSn());
            return;
        }
        //DTO를 Entity로 변환
        JobPost jobPost = convertToEntity(dto);

        //Null 체크 로직 추가
         if (jobPost == null) {
            log.error("엔티티 변환에 실패했습니다. DTO: {}", dto.getRecrutPblntSn());
            return;
         }

        //DB 저장 (복합 유니크 키 제약으로 중복 방지)
        jobPostRepository.save(jobPost);
        log.info("새로운 공고 저장 완료: {}", dto.getRecrutPbancTtl());
    }

    private JobPost convertToEntity(PublicJobDto dto) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

        return JobPost.builder()
                .sourcePlatform(SourcePlatform.PUBLIC_ALIO)
                .originalSn(dto.getRecrutPblntSn())
                .companyName(dto.getInstNm())
                .title(dto.getRecrutPbancTtl())
                .jobCategory(dto.getNcsCdNmLst())
                .location(dto.getWorkRgnNmLst())
                .hireType(dto.getHireTypeNmLst())
                .startDate(LocalDate.parse(dto.getPbancBgngYmd(), formatter))
                .endDate(LocalDate.parse(dto.getPbancEndYmd(), formatter))
                .originalUrl(dto.getSrcUrl())
                .applyQual(dto.getAplyQlfcCn())
                .processInfo(dto.getScrnprcdrMthdExpln())
                .build();
    }

    public void collectAll() {
        log.info("공공데이터 수집 시작...");
        // 1. 목록 조회
        PublicDataResponse listResponse = apiClient.fetchJobList(0, 100);
        
        if (listResponse == null || listResponse.getResult() == null) {
            log.error("API 응답이 비어있습니다.");
            return;
        }

        for (PublicJobDto item : listResponse.getResult()) {
            // 2. 중복 체크 및 상세 정보 수집 결정
            if (!jobPostRepository.existsBySourcePlatformAndOriginalSn("PUBLIC_ALIO", item.getRecrutPblntSn())) {
                // 3. 신규 데이터라면 상세 API 호출
                PublicDataDetailResponse detailResponse = apiClient.fetchJobDetail(item.getRecrutPblntSn());
                
                if (detailResponse != null && detailResponse.getResult() != null) {
                    PublicJobDto detailDto = detailResponse.getResult();
                    // 4. 엔티티 변환 및 저장
                    processAndSave(detailDto);
                }
            }
        }
        log.info("공공데이터 수집 종료.");
    }
}
