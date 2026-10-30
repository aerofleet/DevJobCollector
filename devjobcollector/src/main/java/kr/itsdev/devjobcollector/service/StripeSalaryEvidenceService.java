package kr.itsdev.devjobcollector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.itsdev.devjobcollector.collection.adapter.StripeSalaryEvidenceParser;
import kr.itsdev.devjobcollector.collection.support.ContentHash;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** Operates on an already-fetched page; no network requests or scheduler are enabled here. */
@Service
@RequiredArgsConstructor
public class StripeSalaryEvidenceService {
    private static final int MAX_HTML_CHARS = 3_000_000;
    private final ObjectMapper mapper;

    public record Preview(String status, String sourceHash, List<String> paragraphs) { }

    public Preview preview(String sourceJobId, String html) {
        if (html == null || html.length() > MAX_HTML_CHARS || sourceJobId == null || !sourceJobId.matches("[0-9]+"))
            return new Preview("UNAVAILABLE", null, List.of());
        var parsed = new StripeSalaryEvidenceParser(mapper).parse(html, sourceJobId);
        return new Preview(parsed.status(), ContentHash.sha256(html), parsed.paragraphs());
    }

}
