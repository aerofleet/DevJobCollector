package kr.itsdev.devjobcollector.collection.discovery;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Low-volume Korean SME seed scan. Network reads finish before persistence starts. */
@Slf4j
@Component
public class HomepageAtsDiscoveryScheduler {
    private final JdbcTemplate jdbc;
    private final SafeHomepageFetcher fetcher;
    private final HomepageAtsCandidateRecorder recorder;
    private final MeterRegistry meters;
    private final AtomicBoolean running = new AtomicBoolean();
    @Value("${collection.homepage-discovery.enabled:true}") private boolean enabled;
    @Value("${collection.homepage-discovery.batch-size:30}") private int batchSize;

    public HomepageAtsDiscoveryScheduler(JdbcTemplate jdbc, SafeHomepageFetcher fetcher,
                                         HomepageAtsCandidateRecorder recorder, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.fetcher = fetcher;
        this.recorder = recorder;
        this.meters = meters;
    }

    @Scheduled(initialDelayString = "${collection.homepage-discovery.initial-delay-ms:120000}",
            fixedDelayString = "${collection.homepage-discovery.delay-ms:3600000}")
    public void discover() {
        if (!enabled || !running.compareAndSet(false, true)) return;
        try {
            List<HomepageAtsCandidateRecorder.Seed> seeds = jdbc.query("SELECT id, homepage_candidate_url, version "
                            + "FROM public_company_seed WHERE status = 'DISCOVERED' AND homepage_candidate_url IS NOT NULL "
                            + "AND (homepage_discovery_checked_at IS NULL OR homepage_discovery_checked_at < "
                            + "UTC_TIMESTAMP(6) - INTERVAL 7 DAY) ORDER BY homepage_discovery_checked_at, id LIMIT ?",
                    (rs, row) -> new HomepageAtsCandidateRecorder.Seed(rs.getLong(1), rs.getString(2), rs.getLong(3)),
                    Math.max(1, Math.min(batchSize, 100)));
            int candidates = 0, checked = 0;
            for (var seed : seeds) {
                try {
                    long started = System.nanoTime();
                    var pages = fetcher.discoverablePages(seed.homepage());
                    meters.timer("company_discovery.homepage.latency", "source", "public_seed")
                            .record(Duration.ofNanos(System.nanoTime() - started));
                    int found = recorder.record(seed, pages);
                    candidates += found;
                    meters.counter("company_discovery.homepage.checked", "source", "public_seed").increment();
                    meters.counter("company_discovery.ats_candidates", "source", "public_seed").increment(found);
                } catch (RuntimeException ignored) {
                    meters.counter("company_discovery.homepage.failed", "source", "public_seed").increment();
                } finally {
                    recorder.markChecked(seed);
                    checked++;
                }
                try { Thread.sleep(500); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); break; }
            }
            List<Existing> companies = jdbc.query("SELECT c.id, c.website_url FROM companies c "
                            + "WHERE c.status = 'VERIFIED' AND c.website_url IS NOT NULL AND TRIM(c.website_url) <> '' "
                            + "AND (c.homepage_discovery_checked_at IS NULL OR c.homepage_discovery_checked_at "
                            + "< UTC_TIMESTAMP(6) - INTERVAL 7 DAY) AND NOT EXISTS "
                            + "(SELECT 1 FROM company_source_target t WHERE t.company_id = c.id) "
                            + "ORDER BY c.homepage_discovery_checked_at, c.id LIMIT ?",
                    (rs, row) -> new Existing(rs.getLong(1), rs.getString(2)), Math.max(1, Math.min(batchSize / 5, 20)));
            for (var company : companies) {
                try {
                    long started = System.nanoTime();
                    var pages = fetcher.discoverablePages(company.homepage());
                    meters.timer("company_discovery.homepage.latency", "source", "existing_company")
                            .record(Duration.ofNanos(System.nanoTime() - started));
                    int found = recorder.recordExisting(company.id(), company.homepage(), pages);
                    candidates += found;
                    meters.counter("company_discovery.homepage.checked", "source", "existing_company").increment();
                    meters.counter("company_discovery.ats_candidates", "source", "existing_company").increment(found);
                } catch (RuntimeException ignored) {
                    meters.counter("company_discovery.homepage.failed", "source", "existing_company").increment();
                } finally {
                    recorder.markExistingChecked(company.id(), company.homepage());
                    checked++;
                }
                try { Thread.sleep(500); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); break; }
            }
            log.info("Homepage ATS discovery completed: checked={}, candidates={}", checked, candidates);
        } catch (Exception ex) {
            log.warn("Public seed homepage discovery failed: {}", ex.getClass().getSimpleName());
        } finally {
            running.set(false);
        }
    }

    private record Existing(long id, String homepage) { }
}
