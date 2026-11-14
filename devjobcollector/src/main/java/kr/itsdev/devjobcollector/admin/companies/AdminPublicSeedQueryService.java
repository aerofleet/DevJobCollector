package kr.itsdev.devjobcollector.admin.companies;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminPublicSeedQueryService {
    private final JdbcTemplate jdbc;
    public AdminPublicSeedQueryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public List<SeedView> list(long afterId, int size) {
        if (afterId < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_SEED_PAGE");
        }
        return select("WHERE s.id > ? ORDER BY s.id LIMIT ?", afterId, size);
    }

    @Transactional(readOnly = true)
    public SeedPage page(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_SEED_PAGE");
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM public_company_seed", Long.class);
        long total = count == null ? 0 : count;
        long totalPages = (total + size - 1) / size;
        int current = totalPages == 0 ? 0 : (int) Math.min((long) page, totalPages - 1);
        return new SeedPage(select("ORDER BY s.id LIMIT ? OFFSET ?", size, (long) current * size),
                current, size, total, totalPages);
    }

    private List<SeedView> select(String suffix, Object... params) {
        return jdbc.query("SELECT s.id, s.company_name, s.source, s.status, s.last_seen_at, s.homepage_candidate_url, s.version, "
                        + "s.homepage_candidate_source, s.homepage_candidate_score, "
                        + "s.has_it_related_posting, s.relevant_posting_title, "
                        + "c.id AS matched_company_id, c.display_name AS matched_company_name, c.status AS company_status "
                        + "FROM public_company_seed s LEFT JOIN companies c "
                        + "ON c.business_number_hash = s.business_number_hash " + suffix,
                (rs, row) -> new SeedView(rs.getLong("id"), rs.getString("company_name"), rs.getString("source"),
                        rs.getString("status"), rs.getObject("last_seen_at", LocalDateTime.class),
                        rs.getObject("matched_company_id", Long.class), rs.getString("matched_company_name"),
                        rs.getString("company_status"), rs.getString("homepage_candidate_url"), rs.getLong("version"),
                        rs.getBoolean("has_it_related_posting"), rs.getString("relevant_posting_title"),
                        rs.getString("homepage_candidate_source"), (Integer) rs.getObject("homepage_candidate_score")), params);
    }

    public record SeedPage(List<SeedView> items, int page, int size, long totalElements, long totalPages) { }

    public record SeedView(long id, String companyName, String source, String status, LocalDateTime lastSeenAt,
                           Long matchedCompanyId, String matchedCompanyName, String companyStatus,
                           String homepageCandidateUrl, long version, boolean itRelatedPosting, String relevantPostingTitle,
                           String homepageCandidateSource, Integer homepageCandidateScore) { }
}
