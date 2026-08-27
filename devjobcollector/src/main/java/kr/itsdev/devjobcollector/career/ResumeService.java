package kr.itsdev.devjobcollector.career;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import kr.itsdev.devjobcollector.dto.career.ResumeDetailResponse;
import kr.itsdev.devjobcollector.dto.career.ResumeStatusUpdateRequest;
import kr.itsdev.devjobcollector.dto.career.ResumeSummaryResponse;
import kr.itsdev.devjobcollector.dto.career.ResumeUpsertRequest;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ResumeService {
    private final CurrentMemberService currentMemberService;
    private final CareerResumeRepository resumeRepository;
    private final ObjectMapper objectMapper;

    public ResumeService(
            CurrentMemberService currentMemberService,
            CareerResumeRepository resumeRepository,
            ObjectMapper objectMapper
    ) {
        this.currentMemberService = currentMemberService;
        this.resumeRepository = resumeRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ResumeSummaryResponse> list(String subject) {
        UserAccount member = currentMemberService.requireCurrentMember(subject);
        return resumeRepository.findAllByUser_IdOrderByUpdatedAtDescIdDesc(member.getId()).stream()
                .map(ResumeSummaryResponse::from)
                .toList();
    }

    @Transactional
    public ResumeDetailResponse create(String subject, ResumeUpsertRequest request) {
        UserAccount member = currentMemberService.requireCurrentMember(subject);
        ObjectNode content = normalizeContent(request.content());
        CareerResume resume = resumeRepository.save(CareerResume.draft(
                member,
                request.title(),
                writeContent(content)
        ));
        return detail(resume);
    }

    @Transactional(readOnly = true)
    public ResumeDetailResponse get(String subject, Long resumeId) {
        UserAccount member = currentMemberService.requireCurrentMember(subject);
        return detail(requireOwnedResume(resumeId, member.getId()));
    }

    @Transactional
    public ResumeDetailResponse update(String subject, Long resumeId, ResumeUpsertRequest request) {
        UserAccount member = currentMemberService.requireCurrentMember(subject);
        CareerResume resume = requireOwnedResume(resumeId, member.getId());
        resume.update(request.title(), writeContent(normalizeContent(request.content())));
        return detail(resume);
    }

    @Transactional
    public ResumeDetailResponse changeStatus(
            String subject, Long resumeId, ResumeStatusUpdateRequest request
    ) {
        UserAccount member = currentMemberService.requireCurrentMember(subject);
        CareerResume resume = requireOwnedResume(resumeId, member.getId());
        resume.changeStatus(request.status());
        return detail(resume);
    }

    @Transactional
    public void delete(String subject, Long resumeId) {
        UserAccount member = currentMemberService.requireCurrentMember(subject);
        CareerResume resume = requireOwnedResume(resumeId, member.getId());
        resumeRepository.delete(resume);
    }

    private CareerResume requireOwnedResume(Long resumeId, Long userId) {
        return resumeRepository.findByIdAndUser_Id(resumeId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume not found"));
    }

    private ObjectNode normalizeContent(JsonNode content) {
        if (content == null || !content.isObject()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume content must be an object");
        }
        ObjectNode normalized = ((ObjectNode) content).deepCopy();
        normalizeObject(normalized, "basicInfo");
        normalizeArray(normalized, "techStack");
        normalizeArray(normalized, "projects");
        normalizeArray(normalized, "experience");
        return normalized;
    }

    private void normalizeObject(ObjectNode content, String field) {
        JsonNode value = content.get(field);
        if (value == null || value.isNull()) {
            content.set(field, objectMapper.createObjectNode());
        } else if (!value.isObject()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be an object");
        }
    }

    private void normalizeArray(ObjectNode content, String field) {
        JsonNode value = content.get(field);
        if (value == null || value.isNull()) {
            content.set(field, objectMapper.createArrayNode());
        } else if (!value.isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be an array");
        }
    }

    private String writeContent(JsonNode content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid resume content", error);
        }
    }

    private ResumeDetailResponse detail(CareerResume resume) {
        try {
            return ResumeDetailResponse.from(resume, objectMapper.readTree(resume.getContentJson()));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Stored resume content is invalid", error);
        }
    }
}
