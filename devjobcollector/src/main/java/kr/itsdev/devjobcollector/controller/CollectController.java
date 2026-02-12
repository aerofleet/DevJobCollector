package kr.itsdev.devjobcollector.controller;

import kr.itsdev.devjobcollector.service.PublicDataCollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/collect")
public class CollectController {

    private final PublicDataCollectorService collectorService;

    /**
     * 공공데이터포털 채용 공고 수집 실행
     * GET http://localhost:8080/api/v1/collect/public
     */

    @GetMapping("/public")
    public ResponseEntity<String> collectorPublcData() {
        log.info("수동 수집 요청 수신: 공공데이터포털");

        try {
            collectorService.collectAll();
            return ResponseEntity.ok("공공데이터 수집 프로세스가 성공적으로 완료되었습니다.");
        } catch (Exception e) {
            log.error("수집 중 오류 발생: ", e);
            return ResponseEntity.internalServerError().body("수집 중 오류 발생: " + e.getMessage());
        }
    }
}
