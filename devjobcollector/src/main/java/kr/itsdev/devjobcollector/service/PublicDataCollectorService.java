package kr.itsdev.devjobcollector.service;

import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.domain.SourcePlatform;
import kr.itsdev.devjobcollector.dto.PublicDataDetailResponse;
import kr.itsdev.devjobcollector.dto.PublicDataListResponse;
import kr.itsdev.devjobcollector.dto.PublicJobDto;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 공공데이터 포털 채용 공고 수집 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicDataCollectorService {

    private final JobPostRepository jobPostRepository;
    private final PublicDataApiClient apiClient;
    
    /**
     * 매일 오전 10시와 오후 4시에 공공데이터 수집 실행
     * cron = "초 분 시 일 월 요일"
     */
    @Scheduled(cron = "0 0 10,16 * * *")
    public void scheduleCollect() {
        log.info("┌─────────────────────────────────────────────┐");
        log.info("│  [자동 스케줄러] 정기 데이터 수집 시작       │");
        log.info("└─────────────────────────────────────────────┘");
        this.collectAll(100);
    }

    /**
     * 공공데이터 전체 수집 프로세스
     * @param size 조회할 공고 개수
     */
    @Transactional
    public void collectAll(int size) {
        log.info("=== 공공데이터 수집 시작 (요청 개수: {}) ===", size);
        
        long startTime = System.currentTimeMillis();
        int newCount = 0;
        int skipCount = 0;
        int errorCount = 0;

        try {
            // 1. 목록 조회
            PublicDataListResponse listResponse = apiClient.fetchJobList(0, size);
            
            // 디버깅 로그
            log.debug("📡 원본 응답: {}", listResponse);

            // 응답 검증
            if (listResponse == null) {
                log.error("❌ API 응답이 null입니다.");
                return;
            }
            
            // 상세 로그
            log.info("📊 API 응답 상태:");
            log.info("  - resultCode: {}", listResponse.getResultCode());
            log.info("  - resultMsg: {}", listResponse.getResultMsg());
            log.info("  - totalCount: {}", listResponse.getTotalCount());
            log.info("  - result size: {}", 
                listResponse.getResult() != null ? listResponse.getResult().size() : "null");
            log.info("  - isSuccess(): {}", listResponse.isSuccess());
            
            // 성공 여부 확인
            if (!listResponse.isSuccess()) {
                log.error("❌ API 응답 실패: {} - {}", 
                    listResponse.getResultCode(), 
                    listResponse.getResultMsg());
                return;
            }
            
            if (listResponse.getResult() == null) {
                log.error("❌ result 필드가 null입니다.");
                return;
            }
            
            if (listResponse.getResult().isEmpty()) {
                log.warn("⚠️ 조회된 공고가 없습니다.");
                return;
            }

            log.info("✅ API 응답 수신 성공: {} 건", listResponse.getResult().size());

            // 2. 각 공고 처리
            for (PublicJobDto item : listResponse.getResult()) {
                try {
                    String originalSn = item.getRecrutPblntSn();
                    
                    log.debug("🔍 처리 중: {} - {}", item.getInstNm(), item.getRecrutPbancTtl());
                    
                    // 필수 필드 검증
                    if (!isValidDto(item)) {
                        log.warn("⚠️ 필수 필드 누락: {}", originalSn);
                        errorCount++;
                        continue;
                    }

                    // 중복 체크
                    if (isDuplicate(originalSn)) {
                        log.debug("⏭️ 중복 스킵: {}", originalSn);
                        skipCount++;
                        continue;
                    }

                    // 3. 상세 정보 조회 및 저장
                    if (fetchDetailAndSave(originalSn)) {
                        newCount++;
                        log.info("✅ 저장 완료 [{}/{}]: {} - {}", 
                            newCount, 
                            listResponse.getResult().size(),
                            item.getInstNm(), 
                            item.getRecrutPbancTtl());
                    } else {
                        errorCount++;
                    }

                    // API Rate Limiting 방지
                    Thread.sleep(100);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("❌ 스레드 인터럽트 발생", e);
                    break;
                } catch (Exception e) {
                    log.error("❌ 개별 처리 실패: {}", item.getRecrutPblntSn(), e);
                    errorCount++;
                }
            }

        } catch (Exception e) {
            log.error("❌ 수집 프로세스 중 치명적 오류 발생", e);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            printSummary(size, newCount, skipCount, errorCount, duration);
        }
    }

    /**
     * 상세 정보 조회 및 저장
     */
    private boolean fetchDetailAndSave(String originalSn) {
        try {
            PublicDataDetailResponse detailResponse = apiClient.fetchJobDetail(originalSn);
            
            if (detailResponse == null) {
                log.warn("⚠️ 상세 응답이 null: {}", originalSn);
                return false;
            }
            
            if (!detailResponse.isSuccess()) {
                log.warn("⚠️ 상세 조회 실패: {} - {}", 
                    detailResponse.getResultCode(), 
                    detailResponse.getResultMsg());
                return false;
            }
            
            if (detailResponse.getResult() == null) {
                log.warn("⚠️ 상세 데이터 없음: {}", originalSn);
                return false;
            }

            return saveJobPost(detailResponse.getResult());

        } catch (Exception e) {
            log.error("❌ 상세 조회 중 오류: {}", originalSn, e);
            return false;
        }
    }

    /**
     * JobPost 저장
     */
    @Transactional
    public boolean saveJobPost(PublicJobDto dto) {
        try {
            // 재차 중복 체크 (동시성 이슈 대비)
            if (isDuplicate(dto.getRecrutPblntSn())) {
                log.debug("⏭️ 중복 감지 (재확인): {}", dto.getRecrutPblntSn());
                return false;
            }

            // DTO → Entity 변환
            JobPost jobPost = convertToEntity(dto);
            
            if (jobPost == null) {
                log.error("❌ 엔티티 변환 실패: {}", dto.getRecrutPblntSn());
                return false;
            }

            // DB 저장
            jobPostRepository.save(jobPost);
            log.debug("💾 DB 저장 완료: {}", dto.getRecrutPblntSn());
            return true;

        } catch (Exception e) {
            log.error("❌ 저장 중 오류: {}", dto.getRecrutPblntSn(), e);
            return false;
        }
    }

    /**
     * DTO → Entity 변환
     */
    private JobPost convertToEntity(PublicJobDto dto) {
        try {
            LocalDate startDate = dto.getStartDate();
            LocalDate endDate = dto.getEndDate();

            if (startDate == null || endDate == null) {
                log.warn("⚠️ 날짜 정보 누락: {} (시작: {}, 종료: {})", 
                    dto.getRecrutPblntSn(), 
                    dto.getPbancBgngYmd(), 
                    dto.getPbancEndYmd());
                return null;
            }

            return JobPost.builder()
                .sourcePlatform(SourcePlatform.PUBLIC_ALIO)
                .originalSn(dto.getRecrutPblntSn())
                .companyName(dto.getInstNm())
                .title(dto.getRecrutPbancTtl())
                .jobCategory(dto.getNcsCdNmLst())
                .location(dto.getWorkRgnNmLst())
                .hireType(dto.getHireTypeNmLst())
                .startDate(startDate)
                .endDate(endDate)
                .originalUrl(dto.getSrcUrl())
                .applyQual(dto.getAplyQlfcCn())
                .processInfo(dto.getScrnprcdrMthdExpln())
                .build();

        } catch (Exception e) {
            log.error("❌ 엔티티 변환 중 오류: {}", dto.getRecrutPblntSn(), e);
            return null;
        }
    }

    /**
     * DTO 필수 필드 검증
     */
    private boolean isValidDto(PublicJobDto dto) {
        boolean valid = dto.getRecrutPblntSn() != null
            && dto.getInstNm() != null
            && dto.getRecrutPbancTtl() != null
            && dto.getSrcUrl() != null;
        
        if (!valid) {
            log.debug("검증 실패 - SN: {}, 회사: {}, 제목: {}, URL: {}",
                dto.getRecrutPblntSn(),
                dto.getInstNm(),
                dto.getRecrutPbancTtl(),
                dto.getSrcUrl());
        }
        
        return valid;
    }

    /**
     * 중복 체크
     */
    private boolean isDuplicate(String originalSn) {
        return jobPostRepository.existsBySourcePlatformAndOriginalSn(
            SourcePlatform.PUBLIC_ALIO, 
            originalSn
        );
    }

    /**
     * 수집 결과 요약 출력
     */
    private void printSummary(int requested, int saved, int skipped, int error, long duration) {
        log.info("┌─────────────────────────────────────────────┐");
        log.info("│           📊 수집 결과 보고서                 │");
        log.info("├─────────────────────────────────────────────┤");
        log.info("│  요청 개수: {} 건", String.format("%5d", requested));
        log.info("│  신규 저장: {} 건 ✅", String.format("%5d", saved));
        log.info("│  중복 스킵: {} 건 ⏭️", String.format("%5d", skipped));
        log.info("│  실패/오류: {} 건 ❌", String.format("%5d", error));
        log.info("│  소요 시간: {} ms ⏱️", String.format("%5d", duration));
        log.info("│  성공률: {}%", 
            requested > 0 ? String.format("%5.1f", (saved * 100.0 / requested)) : "N/A");
        log.info("└─────────────────────────────────────────────┘");
    }
    
    /**
     * 애플리케이션 시작 시 즉시 수집 (테스트용)
     * 프로덕션 환경에서는 주석 처리 권장
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initCollect() {
        log.info("┌─────────────────────────────────────────────┐");
        log.info("│  🚀 [시스템 시작] 초기 데이터 수집 시작        │");
        log.info("└─────────────────────────────────────────────┘");
        this.collectAll(10);
    }
}