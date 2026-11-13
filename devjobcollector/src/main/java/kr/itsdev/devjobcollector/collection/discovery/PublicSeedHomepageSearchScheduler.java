package kr.itsdev.devjobcollector.collection.discovery;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Finds likely corporate homepages for IT-related public seeds; candidates remain unverified. */
@Slf4j
@Component
public class PublicSeedHomepageSearchScheduler {
    private final JdbcTemplate jdbc;
    private final NaverCompanyHomepageSearchClient search;
    private final AtomicBoolean running = new AtomicBoolean();
    @Value("${collection.homepage-search.enabled:true}") private boolean enabled;
    @Value("${collection.homepage-search.batch-size:30}") private int batchSize;

    public PublicSeedHomepageSearchScheduler(JdbcTemplate jdbc, NaverCompanyHomepageSearchClient search) {
        this.jdbc = jdbc;
        this.search = search;
    }

    @Scheduled(initialDelayString = "${collection.homepage-search.initial-delay-ms:180000}",
            fixedDelayString = "${collection.homepage-search.delay-ms:3600000}")
    public void searchMissingHomepages() {
        if (!enabled || !search.isConfigured() || !running.compareAndSet(false, true)) return;
        int checked = 0;
        int suggestions = 0;
        try {
            List<Seed> seeds = jdbc.query("SELECT id, company_name FROM public_company_seed "
                            + "WHERE status = 'DISCOVERED' AND has_it_related_posting = 1 "
                            + "AND homepage_candidate_url IS NULL AND (homepage_search_checked_at IS NULL "
                            + "OR homepage_search_checked_at < UTC_TIMESTAMP(6) - INTERVAL 30 DAY) "
                            + "ORDER BY homepage_search_checked_at, id LIMIT ?",
                    (rs, row) -> new Seed(rs.getLong("id"), rs.getString("company_name")),
                    Math.max(1, Math.min(batchSize, 100)));
            for (Seed seed : seeds) {
                try {
                    List<NaverCompanyHomepageSearchClient.Suggestion> found = search.search(seed.name());
                    jdbc.update("UPDATE public_company_seed SET homepage_search_checked_at = UTC_TIMESTAMP(6) WHERE id = ?",
                            seed.id());
                    checked++;
                    if (!found.isEmpty()) {
                        var top = found.get(0);
                        suggestions += jdbc.update("UPDATE public_company_seed SET homepage_candidate_url = ?, "
                                        + "homepage_candidate_source = 'NAVER_WEB_SEARCH', homepage_candidate_score = ?, "
                                        + "version = version + 1 WHERE id = ? AND status = 'DISCOVERED' "
                                        + "AND homepage_candidate_url IS NULL",
                                top.url(), top.score(), seed.id());
                    }
                } catch (NaverCompanyHomepageSearchClient.SearchQuotaExceededException ex) {
                    log.warn("Public seed homepage search stopped at the configured NAVER API HUB quota");
                    break;
                } catch (RuntimeException ex) {
                    log.warn("Public seed homepage search failed: seed={}, error={}", seed.id(), ex.getClass().getSimpleName());
                }
                try { Thread.sleep(500); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); break; }
            }
            log.info("Public seed homepage search completed: checked={}, suggestions={}", checked, suggestions);
        } finally {
            running.set(false);
        }
    }

    private record Seed(long id, String name) { }
}
