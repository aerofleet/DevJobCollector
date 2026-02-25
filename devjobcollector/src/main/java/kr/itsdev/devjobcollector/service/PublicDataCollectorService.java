package kr.itsdev.devjobcollector.service;

import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.domain.SourcePlatform;
import kr.itsdev.devjobcollector.domain.TechStack;
import kr.itsdev.devjobcollector.dto.PublicDataDetailResponse;
import kr.itsdev.devjobcollector.dto.PublicDataListResponse;
import kr.itsdev.devjobcollector.dto.PublicJobDto;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import kr.itsdev.devjobcollector.repository.TechStackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 공공데이터 포털 채용 공고 수집 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicDataCollectorService {

    private final JobPostRepository jobPostRepository;
    private final TechStackRepository techStackRepository;
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

            // 응답 검증
            if (listResponse == null || !listResponse.isSuccess()) {
                log.error("❌ API 응답이 실패");
                return;
            }

            if (listResponse.getResult() == null || listResponse.getResult().isEmpty()) {
                log.warn("조회된 공고가 없습니다.");
                return;
            }

            log.info("API 응답 수신 성공: {} 건", listResponse.getResult().size());
            
            // 상세 로그
            log.info("📊 API 응답 상태:");
            log.info("  - resultCode: {}", listResponse.getResultCode());
            log.info("  - resultMsg: {}", listResponse.getResultMsg());
            log.info("  - totalCount: {}", listResponse.getTotalCount());
            log.info("  - result size: {}", 
                listResponse.getResult() != null ? listResponse.getResult().size() : "null");
            log.info("  - isSuccess(): {}", listResponse.isSuccess());
            
            // 2. 원본 일련번호 일괄 중복 체크 (쿼리 1회)
            List<String> originalSnList = listResponse.getResult().stream()
                    .map(PublicJobDto::getRecrutPblntSn)
                    .filter(Objects::nonNull)
                    .toList();

            Set<String> existingSnSet = new HashSet<>(
                    jobPostRepository.findExistingOriginalSns(SourcePlatform.PUBLIC_ALIO, originalSnList)
            );
            log.debug("✅ 중복 체크 완료: {} / {}건 기존 존재", existingSnSet.size(), originalSnList.size());

            // 3. 각 공고 처리
            for (PublicJobDto item : listResponse.getResult()) {
                String originalSn = null;
                try {
                    originalSn = item.getRecrutPblntSn();
                    
                    log.debug("🔍 처리 중: {} - {}", item.getInstNm(), item.getRecrutPbancTtl());
                    
                    // 필수 필드 검증
                    if (!isValidDto(item)) {
                        log.warn("⚠️ 필수 필드 누락: {}", originalSn);
                        errorCount++;
                        continue;
                    }

                    // 중복 체크 (일괄 조회 결과 사용)
                    if (existingSnSet.contains(originalSn)) {
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

                // 신규 저장 시 중복 집합에 추가해 동일 배치 내 중복 방지
                if (originalSn != null) {
                    existingSnSet.add(originalSn);
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

            if (dto.getNcsCdNmLst() != null && !dto.getNcsCdNmLst().isEmpty()) {
                log.debug("🔧 기술스택 문자열: '{}'", dto.getNcsCdNmLst());

                List<TechStack> techStacks = parseTechStacks(dto.getNcsCdNmLst());
                log.debug("📋 파싱된 기술스택: {}개", techStacks.size());

                for (TechStack techStack : techStacks) {
                    if (techStack != null) {
                        jobPost.addTechStack(techStack);
                        log.debug("추가: {} (ID: {})", techStack.getStackName(), techStack.getId());
                    }
                }

                log.debug("기술 스택 {}개 추가: {}",
                    techStacks.size(),
                    techStacks.stream()
                        .map(TechStack::getStackName)
                        .collect(java.util.stream.Collectors.joining(", ")));
            }

            // DB 저장
            log.debug("💾 저장 시작...");
            JobPost savedJobPost = jobPostRepository.save(jobPost);
            jobPostRepository.flush();

            if (savedJobPost.getId() == null) {
                log.error("❌ 저장 실패: ID가 생성되지 않음");
                return false;
            }

            log.debug("💾 DB 저장 완료: {}", dto.getRecrutPblntSn());
            return true;

        } catch (Exception e) {
            log.error("❌ 저장 중 오류: {}", dto.getRecrutPblntSn(), e);
            return false;
        }
    }

    /**
     * 기술 스택 문자열 파싱
     */
    private List<TechStack> parseTechStacks(String techStackString) {
        List<TechStack> techStacks = new ArrayList<>();
        
        if(techStackString == null || techStackString.trim().isEmpty()) {
            return techStacks;
        }

        log.debug("기술스택 파싱 시작: '{}'", techStackString);

        String[] stackNames = techStackString.split("[,./·\\s]+");

        for (String stackName : stackNames) {
            String trimmedName = stackName.trim();
            
            // 유효성 검사
            if (!isValidStackName(trimmedName)) {
                continue;
            }
            
            try {
                // ✅ 동기화된 방식으로 조회/생성
                TechStack techStack = getOrCreateTechStack(trimmedName);

                if (techStack != null) {
                    techStacks.add(techStack);
                } else {
                    log.warn("기술스택이 null로 반환됨: {}", trimmedName);
                }                
            } catch (Exception e) {
                log.error("❌ 기술스택 처리 실패: {}", trimmedName, e);
            }
        }
        
        log.debug("파싱 완료: {} → {}개", techStackString, techStacks.size());

        return techStacks;
    }

    /**
     * ✅ 기술스택 조회 또는 생성 (동시성 문제 해결)
     */
    @Transactional
    private TechStack getOrCreateTechStack(String stackName) {
        Objects.requireNonNull(stackName, "stackName cannot be null");
        // 1. 먼저 조회 시도
        return techStackRepository.findByStackName(stackName)
                .orElseGet(() -> {
                    try {
                        // 2. 없으면 생성
                        TechStack newStack = TechStack.builder()
                                .stackName(stackName)
                                .build();
                        
                        @SuppressWarnings("null")
                        TechStack saved = techStackRepository.save(newStack);
                        techStackRepository.flush(); // ✅ 즉시 DB 반영
                        
                        log.debug("  ✅ 새 기술스택 생성: {} (id={})", stackName, saved.getId());
                        return saved;
                        
                    } catch (Exception e) {
                        // 3. 동시성으로 인한 중복 생성 시도 시 재조회
                        log.warn("⚠️ 중복 생성 시도 감지, 재조회: {}", stackName);
                        return techStackRepository.findByStackName(stackName)
                                .orElseThrow(() -> new RuntimeException("기술스택 조회/생성 실패: " + stackName));
                    }
                });
    }

        /**
     * 기술스택 이름 유효성 검사
     */
    private boolean isValidStackName(String stackName) {
        if (stackName == null || stackName.isEmpty()) {
            return false;
        }
        
        // 2~50자
        if (stackName.length() < 2 || stackName.length() > 50) {
            log.debug("  ⏭️ 길이 제한: '{}'", stackName);
            return false;
        }
        
        // 숫자만 있는 경우 제외
        if (stackName.matches("^[0-9]+$")) {
            log.debug("  ⏭️ 숫자만: '{}'", stackName);
            return false;
        }
        
        // 특수문자만 있는 경우 제외
        if (stackName.matches("^[^a-zA-Z0-9가-힣]+$")) {
            log.debug("  ⏭️ 특수문자만: '{}'", stackName);
            return false;
        }
        
        return true;
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

            String experience = dto.getRecrutSeNm();
            if (experience == null || experience.trim().isEmpty()) {
                experience = "경력무관";
            }

            return JobPost.builder()
                .sourcePlatform(SourcePlatform.PUBLIC_ALIO)
                .originalSn(dto.getRecrutPblntSn())
                .companyName(dto.getInstNm())
                .title(dto.getRecrutPbancTtl())
                .jobCategory(dto.getNcsCdNmLst())
                // .experience(dto.getRecrutSeNm())
                .experience(experience)                
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
        this.collectAll(50);
    }
}
