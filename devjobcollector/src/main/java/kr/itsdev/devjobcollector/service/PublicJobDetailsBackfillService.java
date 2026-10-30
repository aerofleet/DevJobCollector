package kr.itsdev.devjobcollector.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Incrementally fills fields missing from previously collected public postings. */
@Service
@Slf4j
@RequiredArgsConstructor
public class PublicJobDetailsBackfillService {
    private final JdbcTemplate jdbc;
    private final PublicDataApiClient api;

    @Value("${public-data.collection.enabled:true}")
    private boolean collectionEnabled;

    @Scheduled(initialDelayString = "${public-data.details-backfill.initial-delay-ms:120000}",
            fixedDelayString = "${public-data.details-backfill.delay-ms:600000}")
    public void backfill() {
        if (!collectionEnabled) return;
        List<Row> rows = jdbc.query("SELECT id, original_sn FROM job_posts WHERE source_platform='PUBLIC_ALIO' "
                        + "AND public_details_checked_at IS NULL AND (public_details_attempted_at IS NULL "
                        + "OR public_details_attempted_at < CURRENT_TIMESTAMP - INTERVAL 1 DAY) ORDER BY id LIMIT 5",
                (rs, row) -> new Row(rs.getLong("id"), rs.getString("original_sn")));
        for (Row row : rows) {
            try {
                int claimed = jdbc.update("UPDATE job_posts SET public_details_attempted_at=CURRENT_TIMESTAMP "
                        + "WHERE id=? AND public_details_checked_at IS NULL AND (public_details_attempted_at IS NULL "
                        + "OR public_details_attempted_at < CURRENT_TIMESTAMP - INTERVAL 1 DAY)", row.id());
                if (claimed != 1) continue;
                var response = api.fetchJobDetail(row.originalSn());
                if (response == null || !response.isSuccess() || response.getResult() == null
                        || !row.originalSn().equals(response.getResult().getRecrutPblntSn())) {
                    log.warn("공공기관 공고 상세 보완 실패: {}", row.originalSn());
                    continue;
                }
                var dto = response.getResult();
                if (dto.getFiles() != null) for (var file : dto.getFiles()) {
                    if (file == null || !file.isValid() || file.getAtchFileNm().length() > 255) continue;
                    jdbc.update("INSERT INTO job_files (job_post_id,file_name,file_url) "
                                    + "SELECT ?,?,? WHERE NOT EXISTS "
                                    + "(SELECT 1 FROM job_files WHERE job_post_id=? AND file_name=? AND file_url=?)",
                            row.id(), file.getAtchFileNm(), file.getUrl(), row.id(), file.getAtchFileNm(), file.getUrl());
                }
                jdbc.update("UPDATE job_posts SET public_preference=?, public_preference_conditions=?, public_disqualification=?, "
                                + "public_details_checked_at=CURRENT_TIMESTAMP, version=version+1 WHERE id=? "
                                + "AND source_platform='PUBLIC_ALIO' AND original_sn=? AND public_details_checked_at IS NULL",
                        dto.getPrefCn(), dto.getPrefCondCn(), dto.getDisqlfcRsn(), row.id(), row.originalSn());
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("공공기관 공고 상세 보완 실패: {} ({})", row.originalSn(), e.getClass().getSimpleName());
            }
        }
    }

    private record Row(long id, String originalSn) { }
}
