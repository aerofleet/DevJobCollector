package kr.itsdev.devjobcollector.company;

import java.io.IOException;
import java.util.UUID;
import kr.itsdev.devjobcollector.dto.company.CompanyVerificationSubmitRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import kr.itsdev.devjobcollector.dto.company.CompanyVerificationResponse;

@Service
public class CompanyEvidenceService {
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private final CompanyVerificationService verificationService;
    private final JdbcTemplate jdbcTemplate;

    public CompanyEvidenceService(CompanyVerificationService verificationService, JdbcTemplate jdbcTemplate) {
        this.verificationService = verificationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public CompanyVerificationResponse submit(String subject, Long companyId, MultipartFile file) {
        byte[] content = readAndValidate(file);
        String contentType = detectedType(content);
        String key = "company-verification/" + UUID.randomUUID() + "/evidence";
        CompanyVerificationResponse response = verificationService.submit(subject, companyId,
                new CompanyVerificationSubmitRequest(
                        CompanyVerificationMethod.BUSINESS_REGISTRATION_DOCUMENT, key));
        jdbcTemplate.update("INSERT INTO company_verification_evidence (request_id, content_type, content) VALUES (?, ?, ?)",
                response.requestId(), contentType, content);
        return response;
    }

    @Transactional(readOnly = true)
    public Evidence readForAdmin(String subject, Long requestId) {
        verificationService.requirePlatformAdmin(subject);
        return findEvidence("SELECT content_type, content FROM company_verification_evidence WHERE request_id = ?",
                requestId);
    }

    @Transactional(readOnly = true)
    public Evidence readForAdminCompany(Long companyId, Long requestId) {
        return findEvidence("""
                SELECT e.content_type, e.content FROM company_verification_evidence e
                JOIN company_verification_requests r ON r.id = e.request_id
                WHERE r.company_id = ? AND r.id = ?
                """, companyId, requestId);
    }

    private Evidence findEvidence(String query, Object... parameters) {
        Evidence evidence = jdbcTemplate.query(query,
                result -> result.next()
                        ? new Evidence(result.getString("content_type"), result.getBytes("content"))
                        : null, parameters);
        if (evidence == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "증빙 문서를 찾을 수 없습니다.");
        }
        return evidence;
    }

    private byte[] readAndValidate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "5MB 이하의 사업자등록증 파일을 선택해주세요.");
        }
        try {
            byte[] content = file.getBytes();
            if (content.length > MAX_BYTES || detectedType(content) == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF, PNG, JPG 파일만 제출할 수 있습니다.");
            }
            return content;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일을 읽을 수 없습니다.", exception);
        }
    }

    private String detectedType(byte[] bytes) {
        if (bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D'
                && bytes[3] == 'F' && bytes[4] == '-') return "application/pdf";
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P'
                && bytes[2] == 'N' && bytes[3] == 'G' && bytes[4] == 0x0d
                && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a) return "image/png";
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) return "image/jpeg";
        return null;
    }

    public record Evidence(String contentType, byte[] content) {}
}
