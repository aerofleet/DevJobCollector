package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OpenAiEvidenceClient {
    public static final String MODEL = "gpt-5-nano";
    public static final String PROMPT_VERSION = "evidence-selection-v1";
    private final ObjectMapper mapper;
    private final String apiKey;
    private final boolean enabled;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public OpenAiEvidenceClient(ObjectMapper mapper, @Value("${ai.openai.api-key:}") String apiKey,
                                @Value("${ai.openai.enabled:false}") boolean enabled) {
        this.mapper = mapper; this.apiKey = apiKey; this.enabled = enabled;
    }
    public boolean available() { return enabled && apiKey != null && !apiKey.isBlank(); }

    public JsonNode select(List<RecommendationEvidence.Evidence> input) {
        if (!available()) throw new IllegalStateException("EXPLANATION_UNAVAILABLE");
        try {
            var request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
                    .timeout(Duration.ofSeconds(25)).header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload(input)))).build();
            var future = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            try {
                var response = future.get(25, TimeUnit.SECONDS);
                if (response.statusCode() != 200 || response.body().length() > 200_000)
                    throw new IllegalStateException("PROVIDER_FAILED");
                return parse(response.body());
            } finally { if (!future.isDone()) future.cancel(true); }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("PROVIDER_INTERRUPTED");
        } catch (Exception error) {
            // Do not log or propagate provider messages, Authorization, request/response bodies.
            throw new IllegalStateException("PROVIDER_FAILED");
        }
    }

    Map<String,Object> payload(List<RecommendationEvidence.Evidence> input) {
        if (input.isEmpty() || input.size() > 5) throw new IllegalArgumentException("Invalid evidence count");
        for (int i=0; i<input.size(); i++) {
            if (input.get(i).slot()!=i || input.get(i).evidence().isEmpty()
                    || input.get(i).evidence().size()>8 || input.get(i).evidence().stream().anyMatch(Objects::isNull))
                throw new IllegalArgumentException("Invalid evidence");
        }
        var codeNames = Arrays.stream(RecommendationEvidence.Code.values()).map(Enum::name).toList();
        var jobSchema = Map.of("type","object","additionalProperties",false,"required",List.of("slot","reasons"),
                "properties",Map.of("slot",Map.of("type","integer","minimum",0,"maximum",4),
                        "reasons",Map.of("type","array","minItems",1,"maxItems",3,
                                "items",Map.of("type","string","enum",codeNames))));
        var schema = Map.of("type","object","additionalProperties",false,"required",List.of("jobs"),
                "properties",Map.of("jobs",Map.of("type","array","minItems",1,"maxItems",5,"items",jobSchema)));
        return Map.of(
                "model",MODEL,"store",false,"max_output_tokens",2048,
                "reasoning",Map.of("effort","minimal"),
                "instructions","For each job slot select 1 to 3 useful recommendation evidence codes, in explanatory order. "
                        + "Use ONLY codes present in that slot. Include CHECK_MISSING_SKILLS or CHECK_CAREER when present. "
                        + "Cover each slot once. Never change job order or infer facts. Return the required JSON.",
                "input",json(input),
                "text",Map.of("format",Map.of("type","json_schema","name","recommendation_evidence",
                        "strict",true,"schema",schema)));
    }

    JsonNode parse(String body) throws Exception {
        var response = mapper.readTree(body);
        if (!"completed".equals(response.path("status").asText())) throw new IllegalArgumentException("Incomplete response");
        String text = null;
        for (var output : response.path("output")) {
            if (!"message".equals(output.path("type").asText())) continue;
            for (var content : output.path("content")) {
                if ("refusal".equals(content.path("type").asText())) throw new IllegalArgumentException("Refusal");
                if ("output_text".equals(content.path("type").asText())) {
                    if (text != null || !content.path("text").isTextual()) throw new IllegalArgumentException("Ambiguous response");
                    text = content.path("text").asText();
                }
            }
        }
        if (text == null || text.length()>16_000) throw new IllegalArgumentException("Missing output");
        return mapper.readTree(text);
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception error) { throw new IllegalArgumentException("Invalid evidence"); }
    }
}
