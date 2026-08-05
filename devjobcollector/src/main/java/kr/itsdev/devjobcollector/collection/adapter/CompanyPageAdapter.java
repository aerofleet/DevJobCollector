package kr.itsdev.devjobcollector.collection.adapter;

import kr.itsdev.devjobcollector.collection.config.CompanyPageCollectionProperties;
import kr.itsdev.devjobcollector.collection.domain.CollectionStatus;
import kr.itsdev.devjobcollector.collection.domain.CompanySourceTarget;
import kr.itsdev.devjobcollector.collection.domain.SourceType;
import kr.itsdev.devjobcollector.collection.dto.CollectionContext;
import kr.itsdev.devjobcollector.collection.dto.CollectionResult;
import kr.itsdev.devjobcollector.collection.dto.JobRawDto;
import kr.itsdev.devjobcollector.collection.support.ContentHash;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyPageAdapter implements JobSourceAdapter {

    private static final String SCHEMA_VERSION = "schema-org-job-posting-v1-2026-08";

    private final RestTemplate restTemplate;
    private final JsonLdJobPostingParser parser;
    private final CompanyPageCollectionProperties properties;

    @Override
    public SourceType sourceType() {
        return SourceType.COMPANY_PAGE;
    }

    @Override
    public CollectionResult fetchJobs(CompanySourceTarget target, CollectionContext context) {
        requireTarget(target);
        URI pageUri;
        try {
            pageUri = requirePublicHttpUri(target.getCareersUrl());
        } catch (IllegalArgumentException e) {
            log.warn("Company page target URL rejected: target={}", target.getSourceIdentifier());
            return CollectionResult.failed(CollectionStatus.FAILED, SCHEMA_VERSION);
        }

        try {
            RequestEntity<Void> request = RequestEntity.get(pageUri)
                    .header(HttpHeaders.USER_AGENT, properties.userAgent())
                    .accept(MediaType.TEXT_HTML, MediaType.APPLICATION_XHTML_XML)
                    .build();
            ResponseEntity<String> response = restTemplate.exchange(request, String.class);
            String html = response.getBody();
            if (html == null || html.isBlank()) {
                return CollectionResult.failed(CollectionStatus.SCHEMA_CHANGED, SCHEMA_VERSION);
            }

            JsonLdJobPostingParser.ParseResult parsed = parser.parse(
                    html, pageUri, target.displayCompanyName());
            List<JobRawDto> jobs = parsed.jobs();
            CollectionStatus status = status(parsed);
            return new CollectionResult(
                    status,
                    jobs,
                    jobs.size(),
                    SCHEMA_VERSION,
                    false,
                    ContentHash.sha256(html));
        } catch (HttpStatusCodeException e) {
            CollectionStatus status = e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS
                    ? CollectionStatus.RATE_LIMITED : CollectionStatus.FAILED;
            log.warn("Company page collection failed: target={}, status={}",
                    target.getSourceIdentifier(), e.getStatusCode().value());
            return CollectionResult.failed(status, SCHEMA_VERSION);
        } catch (RestClientException e) {
            log.warn("Company page request failed: target={}", target.getSourceIdentifier());
            return CollectionResult.failed(CollectionStatus.FAILED, SCHEMA_VERSION);
        }
    }

    private static CollectionStatus status(JsonLdJobPostingParser.ParseResult parsed) {
        if (!parsed.jobs().isEmpty()) {
            return parsed.malformedScriptCount() > 0
                    ? CollectionStatus.PARTIAL_SUCCESS : CollectionStatus.SUCCESS;
        }
        if (parsed.jsonLdScriptCount() > 0
                && parsed.jsonLdScriptCount() == parsed.malformedScriptCount()) {
            return CollectionStatus.SCHEMA_CHANGED;
        }
        return CollectionStatus.EMPTY_SUCCESS;
    }

    private static URI requirePublicHttpUri(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("careersUrl is required");
        }
        URI uri = URI.create(value.trim());
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) || host == null) {
            throw new IllegalArgumentException("Public HTTP(S) URL is required");
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (isPrivateHost(normalizedHost)) {
            throw new IllegalArgumentException("Private network targets are not allowed");
        }
        return uri;
    }

    private static boolean isPrivateHost(String host) {
        if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")
                || host.equals("::1") || host.startsWith("fc") || host.startsWith("fd")
                || host.startsWith("fe80:")) {
            return true;
        }
        if (host.startsWith("127.") || host.startsWith("10.") || host.startsWith("192.168.")
                || host.startsWith("169.254.") || host.startsWith("0.")) {
            return true;
        }
        if (!host.startsWith("172.")) {
            return false;
        }
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            int second = Integer.parseInt(parts[1]);
            return second >= 16 && second <= 31;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void requireTarget(CompanySourceTarget target) {
        if (target == null || target.getProvider() != sourceType()) {
            throw new IllegalArgumentException("Company page target is required");
        }
    }
}
