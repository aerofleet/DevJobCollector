package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import kr.itsdev.devjobcollector.career.ResumeService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ResumePreprocessingService {
    private final ResumeService resumes;
    private final ResumeTextExtractor extractor;
    private final ResumePiiGate pii;

    public ResumePreprocessingService(ResumeService resumes, ResumeTextExtractor extractor, ResumePiiGate pii) {
        this.resumes = resumes;
        this.extractor = extractor;
        this.pii = pii;
    }

    public ResumePiiGate.Result preview(String subject, Long resumeId, boolean localProcessingConsent,
                                        MultipartFile file) {
        if (!localProcessingConsent) throw ResumeTextExtractor.failure(HttpStatus.BAD_REQUEST, "LOCAL_PROCESSING_CONSENT_REQUIRED");
        // Ownership and active-member validation happen before any expensive document processing.
        JsonNode content = resumes.get(subject, resumeId).content();
        var knownValues = new ArrayList<String>();
        JsonNode basic = content.path("basicInfo");
        for (String key : List.of("name", "email", "phone", "birthDate", "address")) {
            if (basic.path(key).isTextual()) knownValues.add(basic.path(key).asText());
        }
        String text;
        if (file != null) {
            text = extractor.extract(file);
        } else {
            var result = new StringBuilder();
            for (String key : List.of("techStack", "projects", "experience")) {
                appendText(content.path(key), result, 0);
            }
            text = result.toString();
        }
        // This endpoint neither stores raw input nor sends any content to a provider.
        return pii.mask(text, knownValues);
    }

    private void appendText(JsonNode node, StringBuilder result, int depth) {
        if (depth > 12) throw ResumeTextExtractor.failure(HttpStatus.BAD_REQUEST, "RESUME_NESTING_LIMIT");
        if (node.isTextual()) {
            if (result.length() + node.textValue().length() + 1 > 60_000) {
                throw ResumeTextExtractor.failure(HttpStatus.BAD_REQUEST, "RESUME_TEXT_LIMIT");
            }
            result.append(node.textValue()).append('\n');
        } else if (node.isContainerNode()) {
            node.elements().forEachRemaining(value -> appendText(value, result, depth + 1));
        }
    }
}
