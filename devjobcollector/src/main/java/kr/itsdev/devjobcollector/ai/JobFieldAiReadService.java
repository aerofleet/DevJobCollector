package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kr.itsdev.devjobcollector.domain.JobPost;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Reads only reviewed fields whose source content and version are still current. */
@Service
public class JobFieldAiReadService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final boolean enabled;
    public JobFieldAiReadService(JdbcTemplate jdbc, ObjectMapper mapper, @Value("${ai.job-field.display-enabled:false}") boolean enabled) {
        this.jdbc = jdbc; this.mapper = mapper; this.enabled = enabled;
    }
    public Map<Long, List<String>> approvedFields(List<JobPost> jobs) {
        if (!enabled || jobs.isEmpty()) return Map.of();
        var byId = jobs.stream().collect(Collectors.toMap(JobPost::getId, j -> j, (a,b) -> a));
        String marks = byId.keySet().stream().map(id -> "?").collect(Collectors.joining(","));
        var rows = jdbc.queryForList("SELECT p.job_post_id,p.job_version,p.input_hash,p.policy_version,p.model,p.fields_json FROM job_field_ai_proposals p JOIN job_posts j ON j.id=p.job_post_id AND j.version=p.job_version WHERE p.review_status='APPROVED' AND p.job_post_id IN (" + marks + ") ORDER BY p.reviewed_at DESC,p.id DESC", byId.keySet().toArray());
        Map<Long, List<String>> result = new HashMap<>();
        for (var row : rows) {
            long id = ((Number) row.get("job_post_id")).longValue();
            if (result.containsKey(id) || !JobFieldAiPolicy.VERSION.equals(row.get("policy_version"))
                    || !OpenAiEvidenceClient.CONTENT_MODEL.equals(row.get("model")) || byId.get(id) == null
                    || byId.get(id).getVersion() != ((Number) row.get("job_version")).longValue()) continue;
            try {
                var input = JobFieldAiProcessor.prepareMatching(byId.get(id), (String) row.get("input_hash"));
                var fields = JobFieldAiPolicy.validate(mapper.readTree((String) row.get("fields_json")), input.evidenceSource());
                if (!fields.isEmpty()) result.put(id, fields.stream().map(JobFieldAiPolicy.Field::field).toList());
            } catch (Exception invalid) { /* Malformed or changed proposals are unavailable. */ }
        }
        return Map.copyOf(result);
    }
}
