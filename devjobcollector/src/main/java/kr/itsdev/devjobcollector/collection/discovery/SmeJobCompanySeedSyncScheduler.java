package kr.itsdev.devjobcollector.collection.discovery;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily, idempotent synchronization of public hiring-company records into discovery seeds. */
@Slf4j
@Component
public class SmeJobCompanySeedSyncScheduler {
    private final SmeJobCompaniesApiClient api;
    private final SmeJobCompanySeedImporter importer;
    private final MeterRegistry meters;
    private final AtomicBoolean running = new AtomicBoolean();
    @Value("${collection.sme-seed-sync.enabled:false}") private boolean enabled;
    @Value("${collection.sme-seed-sync.page-size:1000}") private int configuredPageSize;

    public SmeJobCompanySeedSyncScheduler(SmeJobCompaniesApiClient api, SmeJobCompanySeedImporter importer,
                                          MeterRegistry meters) {
        this.api = api;
        this.importer = importer;
        this.meters = meters;
    }

    @Scheduled(initialDelayString = "${collection.sme-seed-sync.initial-delay-ms:300000}",
            fixedDelayString = "${collection.sme-seed-sync.delay-ms:86400000}")
    public void sync() {
        if (!enabled || !api.isConfigured() || !running.compareAndSet(false, true)) return;
        int pageNumber = 1;
        long totalCount = -1;
        long fetchedCount = 0;
        int imported = 0;
        int skipped = 0;
        try {
            int pageSize = Math.max(1, Math.min(configuredPageSize, 1000));
            while (totalCount < 0 || (long) (pageNumber - 1) * pageSize < totalCount) {
                var page = api.fetchPage(pageNumber, pageSize);
                if (totalCount < 0) totalCount = page.totalCount();
                if (page.currentCount() == 0) {
                    if (totalCount >= 0 && fetchedCount >= totalCount) break;
                    throw new IllegalStateException("KOSME API page ended before totalCount");
                }
                imported += importer.importSeeds(page.seeds());
                skipped += page.skipped();
                fetchedCount += page.currentCount();
                meters.counter("company_discovery.sme_seed.api_rows", "result", "imported").increment(page.seeds().size());
                meters.counter("company_discovery.sme_seed.api_rows", "result", "skipped").increment(page.skipped());
                if (totalCount >= 0 && fetchedCount >= totalCount) break;
                if (page.totalCount() < 0 && page.currentCount() < pageSize) break;
                pageNumber++;
                Thread.sleep(250);
            }
            if (totalCount >= 0 && fetchedCount != totalCount) {
                throw new IllegalStateException("KOSME API rows did not match totalCount");
            }
            meters.counter("company_discovery.sme_seed.sync", "result", "success").increment();
            log.info("KOSME SME seed sync completed: pages={}, imported={}, skipped={}, total={}",
                    pageNumber, imported, skipped, totalCount);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("KOSME SME seed sync interrupted after page {}", pageNumber);
        } catch (Exception ex) {
            meters.counter("company_discovery.sme_seed.sync", "result", "failed").increment();
            log.warn("KOSME SME seed sync stopped after page {}: {}", pageNumber, ex.getClass().getSimpleName());
        } finally {
            running.set(false);
        }
    }
}
