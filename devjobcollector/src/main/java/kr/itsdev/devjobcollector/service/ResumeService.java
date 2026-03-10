package kr.itsdev.devjobcollector.service;

import kr.itsdev.devjobcollector.dto.ResumeDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {
    
    private final Map<Long, ResumeDTO> resumeStore = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1L);

    /**
     * 이력서 저장 (신규 또는 수정)
     */
    public ResumeDTO saveResume(ResumeDTO resumeDTO) {
        Objects.requireNonNull(resumeDTO, "이력서 데이터가 null입니다.");
        
        // ✅ 방법 1: Optional.ofNullable (가장 깔끔)
        Long id = Optional.ofNullable(resumeDTO.getId())
                          .orElseGet(sequence::getAndIncrement);
        
        resumeDTO.setId(id);
        resumeStore.put(id, resumeDTO);
        
        log.info("이력서 저장 완료: id={}", id);
        return resumeDTO;
    }

    /**
     * 이력서 조회
     */
    public ResumeDTO getResume(Long userId) {
        return Optional.ofNullable(resumeStore.get(userId))
                       .orElseThrow(() -> new IllegalArgumentException(
                           "이력서를 찾을 수 없습니다. userId: " + userId
                       ));
    }

    /**
     * 이력서 수정
     */
    public ResumeDTO updateResume(Long id, ResumeDTO resumeDTO) {
        Objects.requireNonNull(id, "ID가 null입니다.");
        Objects.requireNonNull(resumeDTO, "이력서 데이터가 null입니다.");
        
        if (!resumeStore.containsKey(id)) {
            throw new IllegalArgumentException("이력서를 찾을 수 없습니다. id: " + id);
        }
        
        resumeDTO.setId(id);
        resumeStore.put(id, resumeDTO);
        
        log.info("이력서 수정 완료: id={}", id);
        return resumeDTO;
    }

    /**
     * 이력서 삭제
     */
    public void deleteResume(Long id) {
        Objects.requireNonNull(id, "ID가 null입니다.");
        
        ResumeDTO removed = resumeStore.remove(id);
        if (removed == null) {
            throw new IllegalArgumentException("이력서를 찾을 수 없습니다. id: " + id);
        }
        
        log.info("이력서 삭제 완료: id={}", id);
    }

    /**
     * 전체 이력서 조회
     */
    public Map<Long, ResumeDTO> getAllResumes() {
        return Map.copyOf(resumeStore);
    }

    /**
     * 이력서 개수 조회
     */
    public int getResumeCount() {
        return resumeStore.size();
    }
}