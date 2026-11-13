package kr.itsdev.devjobcollector.collection.discovery;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HomepageAtsCandidateRecorder {
    private final JdbcTemplate jdbc;
    private final HomepageAtsLinkDiscovery links;
    private final kr.itsdev.devjobcollector.collection.repository.CompanySourceTargetRepository targets;

    public HomepageAtsCandidateRecorder(JdbcTemplate jdbc, HomepageAtsLinkDiscovery links,
                                        kr.itsdev.devjobcollector.collection.repository.CompanySourceTargetRepository targets) {
        this.jdbc = jdbc;
        this.links = links;
        this.targets = targets;
    }

    /** Persists only suggestions; the collection target remains a separate administrator action. */
    @Transactional
    public int record(Seed seed, List<SafeHomepageFetcher.Page> pages) {
        int added = 0;
        for (var page : pages) {
            for (var candidate : links.discover(page.url().toString(), page.html())) {
                if (candidate.sourceUrl().length() > 1000 || targets.findByProviderAndSourceIdentifier(
                        candidate.fingerprint().provider(), candidate.fingerprint().sourceIdentifier()).isPresent()) continue;
                int changed = jdbc.update("INSERT INTO public_seed_ats_candidate "
                                + "(seed_id, provider, source_identifier, source_url, status, first_seen_at, last_seen_at, "
                                + "first_homepage_candidate_url, first_seed_version, discovery_source) "
                                + "VALUES (?, ?, ?, ?, 'DISCOVERED', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), ?, ?, 'AUTO_HOMEPAGE') "
                                + "ON DUPLICATE KEY UPDATE last_seen_at = UTC_TIMESTAMP(6)",
                        seed.id(), candidate.fingerprint().provider().name(), candidate.fingerprint().sourceIdentifier(),
                        candidate.sourceUrl(), seed.homepage(), seed.version());
                added += changed;
            }
        }
        return added;
    }

    @Transactional
    public void markChecked(Seed seed) {
        jdbc.update("UPDATE public_company_seed SET homepage_discovery_checked_at = UTC_TIMESTAMP(6) "
                + "WHERE id = ? AND version = ? AND status = 'DISCOVERED'", seed.id(), seed.version());
    }

    @Transactional
    public int recordExisting(long companyId, String homepage, List<SafeHomepageFetcher.Page> pages) {
        int added = 0;
        for (var page : pages) {
            for (var candidate : links.discover(page.url().toString(), page.html())) {
                if (candidate.sourceUrl().length() > 1000 || targets.findByProviderAndSourceIdentifier(
                        candidate.fingerprint().provider(), candidate.fingerprint().sourceIdentifier()).isPresent()) continue;
                int changed = jdbc.update("INSERT INTO company_discovery_candidate "
                                + "(company_id, provider, source_identifier, source_url, status, first_seen_at, last_seen_at, "
                                + "homepage_url, discovery_source) VALUES (?, ?, ?, ?, 'DISCOVERED', UTC_TIMESTAMP(6), "
                                + "UTC_TIMESTAMP(6), ?, 'AUTO_HOMEPAGE') ON DUPLICATE KEY UPDATE last_seen_at = UTC_TIMESTAMP(6)",
                        companyId, candidate.fingerprint().provider().name(), candidate.fingerprint().sourceIdentifier(),
                        candidate.sourceUrl(), homepage);
                added += changed;
            }
        }
        return added;
    }

    @Transactional
    public void markExistingChecked(long companyId, String homepage) {
        jdbc.update("UPDATE companies SET homepage_discovery_checked_at = UTC_TIMESTAMP(6) "
                + "WHERE id = ? AND website_url = ? AND status = 'VERIFIED'", companyId, homepage);
    }

    public record Seed(long id, String homepage, long version) { }
}
