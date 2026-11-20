package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Owner-approved labels join the built-in catalogue for subsequent proposals. */
@Service
public class JobRoleAiCatalog {
    private final JdbcTemplate jdbc;
    public JobRoleAiCatalog(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Set<String> allowedRoles() {
        Set<String> result = new HashSet<>(JobRoleAiRules.EXISTING_ROLES);
        result.addAll(jdbc.queryForList("SELECT role_label FROM job_role_ai_catalog ORDER BY role_label LIMIT 201", String.class));
        if (result.size() > 200 || result.stream().anyMatch(role -> !JobRoleAiRules.validRoleLabel(role)))
            throw new IllegalStateException("ROLE_CATALOG_INVALID");
        return Set.copyOf(result);
    }

    public static Set<String> decodeSnapshot(ObjectMapper mapper, Object raw) {
        if (!(raw instanceof String text) || text.length() > 10000) throw new IllegalArgumentException("ROLE_CATALOG_INVALID");
        try {
            JsonNode node = mapper.readTree(text);
            if (!node.isArray() || node.size() > 200) throw new IllegalArgumentException("ROLE_CATALOG_INVALID");
            Set<String> roles = new HashSet<>();
            for (JsonNode item : node) {
                if (!item.isTextual() || !JobRoleAiRules.validRoleLabel(item.asText()) || !roles.add(item.asText()))
                    throw new IllegalArgumentException("ROLE_CATALOG_INVALID");
            }
            if (!roles.containsAll(JobRoleAiRules.EXISTING_ROLES)) throw new IllegalArgumentException("ROLE_CATALOG_INVALID");
            return Set.copyOf(roles);
        } catch (JsonProcessingException invalid) { throw new IllegalArgumentException("ROLE_CATALOG_INVALID"); }
    }
}
