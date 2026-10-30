package kr.itsdev.devjobcollector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.itsdev.devjobcollector.collection.adapter.StripeSalaryEvidenceParser;
import kr.itsdev.devjobcollector.collection.support.ContentHash;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.domain.SourcePlatform;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Operates on an already-fetched page; no network requests or scheduler are enabled here. */
@Service
@RequiredArgsConstructor
public class StripeSalaryEvidenceService {
    private static final int MAX_HTML_CHARS = 3_000_000;
    private final JobPostRepository jobs;
    private final ObjectMapper mapper;

    public record Preview(String status, String sourceHash, List<String> paragraphs) { }

    public Preview preview(String sourceJobId, String html) {
        if (html == null || html.length() > MAX_HTML_CHARS || sourceJobId == null || !sourceJobId.matches("[0-9]+"))
            return new Preview("UNAVAILABLE", null, List.of());
        var parsed = new StripeSalaryEvidenceParser(mapper).parse(html, sourceJobId);
        return new Preview(parsed.status(), ContentHash.sha256(html), parsed.paragraphs());
    }

    @Transactional
    public void save(long jobId, String sourceJobId, String html, String reviewedHash, LocalDateTime fetchedAt) {
        JobPost job = jobs.findById(jobId).orElseThrow(() -> new IllegalArgumentException("Job not found"));
        if (job.getSourcePlatform() != SourcePlatform.GREENHOUSE || !job.getOriginalSn().equals("stripe:" + sourceJobId))
            throw new IllegalArgumentException("Job source does not match Stripe posting");
        Preview result = preview(sourceJobId, html);
        if (!"PRESENT".equals(result.status()) || result.sourceHash() == null || !result.sourceHash().equals(reviewedHash))
            throw new IllegalArgumentException("Salary evidence is unavailable or changed since preview");
        job.recordSupplementalSalary(String.join("\n\n", result.paragraphs()),
                "https://stripe.com/jobs/search?gh_jid=" + sourceJobId, result.sourceHash(), fetchedAt);
    }
}
