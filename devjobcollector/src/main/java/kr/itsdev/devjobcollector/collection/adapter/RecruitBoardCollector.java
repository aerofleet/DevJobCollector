package kr.itsdev.devjobcollector.collection.adapter;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import kr.itsdev.devjobcollector.collection.discovery.RecruitBoardIdentity;
import kr.itsdev.devjobcollector.collection.discovery.SafeHomepageFetcher;
import kr.itsdev.devjobcollector.collection.domain.CollectionStatus;
import kr.itsdev.devjobcollector.collection.domain.CompanySourceTarget;
import kr.itsdev.devjobcollector.collection.dto.CollectionResult;
import kr.itsdev.devjobcollector.collection.dto.JobRawDto;
import kr.itsdev.devjobcollector.collection.support.ContentHash;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

/** Bounded collection of explicitly identified recruit boards. An incomplete traversal never closes jobs. */
@Component
public class RecruitBoardCollector {
    static final String SCHEMA = "recruit-board-v1";
    private static final int MAX_PAGES = 30;
    private final SafeHomepageFetcher fetcher;
    private final RecruitBoardParser parser;

    public RecruitBoardCollector(SafeHomepageFetcher fetcher, RecruitBoardParser parser) {
        this.fetcher = fetcher;
        this.parser = parser;
    }

    public CollectionResult collect(CompanySourceTarget target) {
        String boardUrl = target.getCareersUrl();
        var identity = RecruitBoardIdentity.identify(boardUrl);
        if (identity.isEmpty() || !identity.get().equals(target.getSourceIdentifier()))
            return CollectionResult.failed(CollectionStatus.FAILED, SCHEMA);
        URI board = URI.create(boardUrl);
        var pending = new ArrayDeque<Integer>();
        Set<Integer> seen = new LinkedHashSet<>();
        var jobs = new LinkedHashMap<String, JobRawDto>();
        var closed = new LinkedHashSet<String>();
        var hashes = new StringBuilder();
        pending.add(1);
        boolean complete = true;
        while (!pending.isEmpty()) {
            int number = pending.removeFirst();
            if (!seen.add(number)) continue;
            if (seen.size() > MAX_PAGES) { complete = false; break; }
            String pageUrl = number == 1 ? boardUrl : boardUrl + "&page=" + number;
            var page = fetcher.fetchPage(pageUrl);
            if (page.isEmpty()) { complete = false; break; }
            var parsed = parser.parse(page.get().html(), board, target.displayCompanyName());
            if (!parsed.valid()) { complete = false; break; }
            hashes.append(ContentHash.sha256(page.get().html()));
            for (JobRawDto job : parsed.jobs()) {
                if (closed.contains(job.sourceJobId()) || jobs.putIfAbsent(job.sourceJobId(), job) != null) { complete = false; break; }
            }
            for (String id : parsed.closedIds()) {
                if (jobs.containsKey(id) || !closed.add(id)) { complete = false; break; }
            }
            if (!complete) break;
            var pagination = Jsoup.parse(page.get().html()).select(".pagination a[href]");
            for (var link : pagination) {
                String href = link.attr("href").trim();
                if (href.startsWith("javascript:")) continue;
                try {
                    URI uri = board.resolve(href);
                    String prefix = board.getRawQuery() + "&page=";
                    if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                            || !uri.getHost().equalsIgnoreCase(board.getHost()) || uri.getRawUserInfo() != null
                            || (uri.getPort() != -1 && uri.getPort() != 443) || uri.getRawFragment() != null
                            || !board.getRawPath().equals(uri.getRawPath()) || uri.getRawQuery() == null
                            || !uri.getRawQuery().startsWith(prefix)) { complete = false; break; }
                    String value = uri.getRawQuery().substring(prefix.length());
                    if (!value.matches("[1-9][0-9]{0,3}")) { complete = false; break; }
                    int next = Integer.parseInt(value);
                    if (!seen.contains(next)) pending.add(next);
                } catch (IllegalArgumentException ex) { complete = false; break; }
            }
            if (!complete) break;
        }
        if (seen.isEmpty() || (jobs.isEmpty() && !complete))
            return CollectionResult.failed(CollectionStatus.FAILED, SCHEMA);
        var resultJobs = java.util.List.copyOf(jobs.values());
        CollectionStatus status = !complete ? CollectionStatus.PARTIAL_SUCCESS
                : resultJobs.isEmpty() ? CollectionStatus.EMPTY_SUCCESS : CollectionStatus.SUCCESS;
        // Pagination markup can disappear without proving the board is complete. Only explicit
        // closed badges may close jobs; absence from the traversed pages is not sufficient.
        return new CollectionResult(status, resultJobs, resultJobs.size(), SCHEMA,
                false, ContentHash.sha256(hashes.toString()), complete ? closed : Set.of());
    }
}
