package kr.itsdev.devjobcollector.collection.discovery;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Persists suggestions only. Existing review status and evidence are never overwritten by rediscovery. */
@Repository
public class DiscoveryCandidateStore {
    private final JdbcTemplate jdbc;

    public DiscoveryCandidateStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void record(Long companyId, List<HomepageAtsLinkDiscovery.Candidate> candidates) {
        if (companyId == null || companyId <= 0) throw new IllegalArgumentException("companyId is required");
        for (var candidate : candidates) {
            if (candidate.sourceUrl().length() > 1000) continue;
            jdbc.update("INSERT INTO company_discovery_candidate "
                            + "(company_id, provider, source_identifier, source_url, status, first_seen_at, last_seen_at) "
                            + "VALUES (?, ?, ?, ?, 'DISCOVERED', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)) "
                            + "ON DUPLICATE KEY UPDATE last_seen_at = UTC_TIMESTAMP(6)",
                    companyId, candidate.fingerprint().provider().name(),
                    candidate.fingerprint().sourceIdentifier(), candidate.sourceUrl());
        }
    }
}
