package kr.itsdev.devjobcollector.repository;

import kr.itsdev.devjobcollector.domain.JobPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

/**
 * Querydsl 기반 성능 최적화용 커스텀 리포지토리 인터페이스.
 */
public interface JobPostRepositoryCustom {

    /**
     * 전체 필드 통합 검색 (COUNT 쿼리 최적화)
     */
    Page<JobPost> searchByAllFieldsOptimized(String keyword, LocalDate today, Pageable pageable);

    /**
     * 기술 스택 다중 검색 (COUNT 최적화)
     */
    Page<JobPost> findByTechStackNamesOptimized(List<String> stackNames, LocalDate today, Pageable pageable);
}
