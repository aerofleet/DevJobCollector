package kr.itsdev.devjobcollector.company;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.util.Iterator;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
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
    private static final long MAX_IMAGE_PIXELS = 20_000_000L;
    private final CompanyVerificationService verificationService;
    private final JdbcTemplate jdbcTemplate;

    public CompanyEvidenceService(CompanyVerificationService verificationService, JdbcTemplate jdbcTemplate) {
        this.verificationService = verificationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public CompanyVerificationResponse submit(String subject, Long companyId, MultipartFile file) {
        ValidatedFile validated = readAndValidate(file);
        byte[] content = validated.content();
        String contentType = validated.contentType();
        String key = "company-verification/" + UUID.randomUUID() + "/evidence";
        CompanyVerificationResponse response = verificationService.submit(subject, companyId,
                new CompanyVerificationSubmitRequest(
                        CompanyVerificationMethod.BUSINESS_REGISTRATION_DOCUMENT, key));
        jdbcTemplate.update("INSERT INTO company_verification_evidence (request_id, content_type, content) VALUES (?, ?, ?)",
                response.requestId(), contentType, content);
        return response;
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
        if (!java.util.Set.of("application/pdf", "image/png", "image/jpeg").contains(evidence.contentType())
                || evidence.content().length > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "증빙 문서를 찾을 수 없습니다.");
        }
        if (evidence.contentType().equals("application/pdf")) {
            if (!hasPdfEndMarker(evidence.content())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "증빙 문서를 찾을 수 없습니다.");
            }
            return evidence;
        }
        try {
            return new Evidence(evidence.contentType(), normalizedImage(evidence.content(), evidence.contentType()));
        } catch (IOException | ResponseStatusException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "증빙 문서를 찾을 수 없습니다.");
        }
    }

    private ValidatedFile readAndValidate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "5MB 이하의 사업자등록증 파일을 선택해주세요.");
        }
        try {
            byte[] content = file.getBytes();
            String type = detectedType(content);
            if (content.length > MAX_BYTES || type == null || !extensionMatches(file.getOriginalFilename(), type)
                    || !declaredTypeMatches(file.getContentType(), type)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF, PNG, JPG 파일만 제출할 수 있습니다.");
            }
            if (type.equals("application/pdf")) {
                if (!hasPdfEndMarker(content)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 PDF 파일이 아닙니다.");
                }
                return new ValidatedFile(type, content);
            }
            return new ValidatedFile(type, normalizedImage(content, type));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일을 읽을 수 없습니다.", exception);
        }
    }

    private boolean extensionMatches(String filename, String type) {
        if (filename == null) return false;
        String lower = filename.toLowerCase(java.util.Locale.ROOT);
        return switch (type) {
            case "application/pdf" -> lower.endsWith(".pdf");
            case "image/png" -> lower.endsWith(".png");
            case "image/jpeg" -> lower.endsWith(".jpg") || lower.endsWith(".jpeg");
            default -> false;
        };
    }

    private boolean declaredTypeMatches(String declared, String detected) {
        return declared == null || declared.isBlank() || declared.equals("application/octet-stream")
                || declared.equalsIgnoreCase(detected);
    }

    private boolean hasPdfEndMarker(byte[] bytes) {
        byte[] marker = "%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        for (int i = Math.max(0, bytes.length - 1024); i <= bytes.length - marker.length; i++) {
            int matched = 0;
            while (matched < marker.length && bytes[i + matched] == marker[matched]) matched++;
            if (matched == marker.length) return true;
        }
        return false;
    }

    private byte[] normalizedImage(byte[] content, String type) throws IOException {
        String format = type.equals("image/png") ? "png" : "jpeg";
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalidImage();
            ImageReader reader = readers.next();
            try {
                if (!reader.getFormatName().equalsIgnoreCase(format)) throw invalidImage();
                reader.setInput(input);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw invalidImage();
                }
                BufferedImage image = reader.read(0);
                if (image == null) throw invalidImage();
                BufferedImage outputImage = image;
                if (format.equals("jpeg")) {
                    outputImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = outputImage.createGraphics();
                    try { graphics.drawImage(image, 0, 0, null); } finally { graphics.dispose(); }
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                if (!ImageIO.write(outputImage, format, output) || output.size() > MAX_BYTES) {
                    throw invalidImage();
                }
                return output.toByteArray();
            } finally {
                reader.dispose();
            }
        }
    }

    private ResponseStatusException invalidImage() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지 파일을 확인할 수 없습니다.");
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
    private record ValidatedFile(String contentType, byte[] content) {}
}
