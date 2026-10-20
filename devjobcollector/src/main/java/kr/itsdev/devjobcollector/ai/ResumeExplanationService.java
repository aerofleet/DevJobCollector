package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ResumeExplanationService {
    public static final String CONSENT_VERSION = "openai-evidence-v1";
    private final JdbcTemplate jdbc;
    private final ResumeAnalysisService analyses;
    private final CurrentMemberService members;
    private final OpenAiEvidenceClient client;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;

    public ResumeExplanationService(JdbcTemplate jdbc, ResumeAnalysisService analyses, CurrentMemberService members,
                                    OpenAiEvidenceClient client, ObjectMapper mapper, PlatformTransactionManager manager) {
        this.jdbc=jdbc; this.analyses=analyses; this.members=members; this.client=client; this.mapper=mapper;
        this.transaction=new TransactionTemplate(manager);
    }

    public ObjectNode explain(String subject, long id, String consentVersion) {
        if (!CONSENT_VERSION.equals(consentVersion)) throw failure(HttpStatus.BAD_REQUEST,"EXTERNAL_CONSENT_REQUIRED");
        // Commit a durable claim BEFORE calling the provider; ambiguous failures are never automatically retried.
        ObjectNode reserved = transaction.execute(status -> {
            long user=members.requireCurrentMember(subject).getId();
            jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE",Long.class,user);
            ObjectNode analysis=analyses.get(subject,id);
            if (!jdbc.queryForList("SELECT analysis_id FROM ai_evidence_explanation WHERE analysis_id=? FOR UPDATE",id).isEmpty()) return null;
            if (!client.available()) throw failure(HttpStatus.SERVICE_UNAVAILABLE,"EXPLANATION_UNAVAILABLE");
            var evidence=RecommendationEvidence.project(analysis);
            if (evidence.isEmpty() || evidence.stream().anyMatch(item -> item.evidence().isEmpty()))
                throw failure(HttpStatus.UNPROCESSABLE_ENTITY,"NO_RECOMMENDATION_EVIDENCE");
            var today=LocalDate.now(ZoneId.of("Asia/Seoul"));
            jdbc.update("INSERT IGNORE INTO ai_external_daily_usage(user_id,usage_date,attempt_count) VALUES(?,?,0)",user,today);
            if (jdbc.update("UPDATE ai_external_daily_usage SET attempt_count=attempt_count+1 WHERE user_id=? AND usage_date=? AND attempt_count<5",user,today)!=1)
                throw failure(HttpStatus.TOO_MANY_REQUESTS,"DAILY_EXPLANATION_LIMIT");
            jdbc.update("INSERT IGNORE INTO ai_external_global_usage(usage_date,attempt_count) VALUES(?,0)",today);
            if (jdbc.update("UPDATE ai_external_global_usage SET attempt_count=attempt_count+1 WHERE usage_date=? AND attempt_count<100",today)!=1)
                throw failure(HttpStatus.TOO_MANY_REQUESTS,"GLOBAL_EXPLANATION_LIMIT");
            jdbc.update("""
                    INSERT INTO ai_evidence_explanation(analysis_id,status,consent_version,gate_version,model,prompt_version)
                    VALUES(?,'PENDING',?,?,?,?)
                    """,id,CONSENT_VERSION,RecommendationEvidence.VERSION,OpenAiEvidenceClient.MODEL,OpenAiEvidenceClient.PROMPT_VERSION);
            return analysis;
        });
        if (reserved != null) {
            String result=null;
            try {
                var evidence=RecommendationEvidence.project(reserved);
                var response=client.select(evidence);
                result=mapper.writeValueAsString(RecommendationEvidence.validateAndRender(response,evidence,reserved));
            } catch (Exception ignored) {
                // Local analysis remains available. Store no raw provider error or invalid model output.
            }
            String saved=result;
            transaction.executeWithoutResult(status -> jdbc.update("""
                    UPDATE ai_evidence_explanation SET status=?,result_json=?,completed_at=CURRENT_TIMESTAMP(6)
                    WHERE analysis_id=? AND status='PENDING'
                    """,saved==null?"FAILED":"COMPLETED",saved,id));
        }
        return get(subject,id);
    }

    public ObjectNode get(String subject,long id) {
        ObjectNode analysis=analyses.get(subject,id); // ownership and current job visibility
        var result=mapper.createObjectNode();
        result.put("analysisId",id);
        result.put("model",OpenAiEvidenceClient.MODEL);
        result.put("payloadPolicy",RecommendationEvidence.VERSION);
        result.put("externalAttemptCount",0);
        var highlights=mapper.createArrayNode();
        result.set("jobs",highlights);
        var rows=jdbc.queryForList("""
                SELECT status,result_json,TIMESTAMPDIFF(SECOND,created_at,CURRENT_TIMESTAMP(6)) age_seconds
                FROM ai_evidence_explanation WHERE analysis_id=?
                """,id);
        if(rows.isEmpty()) { result.put("status","NOT_REQUESTED"); return result; }
        result.put("externalAttemptCount",1);
        var row=rows.getFirst();
        String state=row.get("status").toString();
        if(state.equals("PENDING") && ((Number)row.get("age_seconds")).longValue()>120) state="INTERRUPTED";
        result.put("status",state);
        if(!state.equals("COMPLETED")) return result;
        try {
            var visible=new HashSet<Long>();
            analysis.path("recommendations").forEach(job->visible.add(job.path("jobId").asLong()));
            for(var item:mapper.readTree(row.get("result_json").toString())) {
                if(visible.contains(item.path("jobId").asLong())) highlights.add(item);
            }
        } catch(Exception error) { throw new IllegalStateException("Invalid saved explanation"); }
        return result;
    }
    private static org.springframework.web.server.ResponseStatusException failure(HttpStatus status,String code) {
        return ResumeTextExtractor.failure(status,code);
    }
}
