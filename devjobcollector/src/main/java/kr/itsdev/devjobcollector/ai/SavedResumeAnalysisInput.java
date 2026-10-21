package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Extract saved documents outside the analysis transaction; revalidate the snapshot before saving. */
@Service
public class SavedResumeAnalysisInput {
    public static final String VERSION = "saved-resume-v1";
    private final JdbcTemplate jdbc;
    private final CurrentMemberService members;
    private final ObjectMapper mapper;
    private final LocalRecommendationEngine engine;
    private final ResumeTextExtractor extractor;

    public SavedResumeAnalysisInput(JdbcTemplate jdbc, CurrentMemberService members, ObjectMapper mapper,
                                    LocalRecommendationEngine engine, ResumeTextExtractor extractor) {
        this.jdbc = jdbc; this.members = members; this.mapper = mapper; this.engine = engine; this.extractor = extractor;
    }

    public Prepared prepare(String subject, long resumeId) {
        long user = members.requireCurrentMember(subject).getId();
        var rows = jdbc.queryForList("SELECT content_json FROM resumes WHERE id=? AND user_id=?", String.class, resumeId, user);
        if (rows.isEmpty()) throw ResumeTextExtractor.failure(HttpStatus.NOT_FOUND, "RESUME_NOT_FOUND");
        JsonNode content;
        try { content = mapper.readTree(rows.getFirst()); }
        catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw new IllegalStateException("Invalid saved resume"); }
        var attachmentIds = jdbc.queryForList("SELECT id FROM resume_attachments WHERE resume_id=? ORDER BY id", Long.class, resumeId);
        if (attachmentIds.size() > 5) throw ResumeTextExtractor.failure(HttpStatus.BAD_REQUEST, "ATTACHMENT_LIMIT");
        var text = new StringBuilder();
        append(content.path("projects"), text, 0);
        append(content.path("experience"), text, 0);
        var sources = new ArrayList<Map<String, Object>>();
        for (long id : attachmentIds) {
            var files = jdbc.queryForList("SELECT filename,content_type,content FROM resume_attachments WHERE id=? AND resume_id=?", id, resumeId);
            if (files.isEmpty()) throw ResumeTextExtractor.failure(HttpStatus.CONFLICT, "RESUME_CHANGED");
            var file = files.getFirst();
            String extracted;
            try {
                extracted = extractor.extractStored(file.get("filename").toString(), file.get("content_type").toString(), (byte[]) file.get("content"));
            } catch (ResponseStatusException error) {
                // Return a bounded identifier, never raw document text or parser exception details.
                throw new AttachmentReadFailure(id, error.getStatusCode().value() == 429 ? HttpStatus.TOO_MANY_REQUESTS
                        : HttpStatus.UNPROCESSABLE_ENTITY);
            }
            add(text, extracted);
            sources.add(Map.of("id", id, "filename", file.get("filename").toString(), "status", "READ"));
        }
        var skills = new TreeSet<>(engine.skills(content));
        var vocabulary = jdbc.queryForList("SELECT stack_name FROM tech_stacks ORDER BY id", String.class);
        skills.addAll(findSkills(text.toString(), vocabulary));
        if (skills.size() > 50) throw ResumeTextExtractor.failure(HttpStatus.UNPROCESSABLE_ENTITY, "TOO_MANY_SKILLS");
        var preferences = new LocalRecommendationEngine.Preferences(careerYears(content), "", "", "");
        return new Prepared(ResumeAnalysisService.hash(rows.getFirst()), List.copyOf(attachmentIds),
                Collections.unmodifiableSet(skills), preferences, List.copyOf(sources));
    }

    static Set<String> findSkills(String text, List<String> vocabulary) {
        var found = new TreeSet<String>();
        for (String skill : vocabulary) {
            if (skill == null || skill.isBlank() || skill.length() > 50) continue;
            // Boundaries prevent Java matching JavaScript and C matching CSS. Single-letter skills are ambiguous in prose.
            if (skill.length() < 2) continue;
            var matcher = Pattern.compile("(?<![\\p{L}\\p{N}_+#])" + Pattern.quote(skill)
                    + "(?![\\p{L}\\p{N}_+#])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text);
            if (matcher.find()) found.add(LocalRecommendationEngine.normalizeSkill(skill));
        }
        return found;
    }

    static Integer careerYears(JsonNode content) {
        var intervals = new ArrayList<LocalDate[]>();
        for (JsonNode item : content.path("experience")) {
            try {
                LocalDate start = LocalDate.parse(item.path("startDate").asText());
                LocalDate end = LocalDate.parse(item.path("endDate").asText());
                if (end.isBefore(start) || end.isAfter(LocalDate.now()) || start.isBefore(LocalDate.now().minusYears(60))) return null;
                intervals.add(new LocalDate[]{start, end});
            } catch (java.time.format.DateTimeParseException error) {
                // A blank end date is not an explicit declaration of current employment.
                return null;
            }
        }
        if (intervals.isEmpty()) return null;
        intervals.sort(Comparator.comparing(value -> value[0]));
        LocalDate start = intervals.getFirst()[0], end = intervals.getFirst()[1];
        long months = 0;
        for (var interval : intervals.subList(1, intervals.size())) {
            if (!interval[0].isAfter(end)) { if (interval[1].isAfter(end)) end = interval[1]; }
            else { months += ChronoUnit.MONTHS.between(start, end); start = interval[0]; end = interval[1]; }
        }
        return (int) Math.min(60, (months + ChronoUnit.MONTHS.between(start, end)) / 12);
    }

    private static void append(JsonNode node, StringBuilder result, int depth) {
        if (depth > 12) throw ResumeTextExtractor.failure(HttpStatus.BAD_REQUEST, "RESUME_NESTING_LIMIT");
        if (node.isTextual()) add(result, node.asText());
        else if (node.isContainerNode()) node.elements().forEachRemaining(child -> append(child, result, depth + 1));
    }
    private static void add(StringBuilder target, String text) {
        if (target.length() + text.length() + 1 > 300_000) throw ResumeTextExtractor.failure(HttpStatus.UNPROCESSABLE_ENTITY, "RESUME_TEXT_LIMIT");
        target.append(text).append('\n');
    }
    public record Prepared(String contentHash, List<Long> attachmentIds, Set<String> skills,
                           LocalRecommendationEngine.Preferences preferences, List<Map<String, Object>> attachments) {}
    public static final class AttachmentReadFailure extends ResponseStatusException {
        private final long attachmentId;
        AttachmentReadFailure(long attachmentId, HttpStatus status) {
            super(status, "ATTACHMENT_READ_FAILED"); this.attachmentId = attachmentId;
        }
        public long attachmentId() { return attachmentId; }
    }
}
