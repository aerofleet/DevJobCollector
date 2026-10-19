package kr.itsdev.devjobcollector.career;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ResumeAttachmentService {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public static final int MAX_FILES = 5;
    private final JdbcTemplate jdbc;
    private final CurrentMemberService members;
    private final CompanyResumeService companyResumes;

    public ResumeAttachmentService(JdbcTemplate jdbc, CurrentMemberService members, CompanyResumeService companyResumes) {
        this.jdbc = jdbc;
        this.members = members;
        this.companyResumes = companyResumes;
    }

    @Transactional(readOnly = true)
    public List<Attachment> list(String subject, Long resumeId) {
        requireOwner(subject, resumeId, false);
        return metadata(resumeId);
    }

    @Transactional
    public Attachment upload(String subject, Long resumeId, MultipartFile file) {
        // Lock the parent so concurrent uploads cannot bypass the per-resume limit.
        requireOwner(subject, resumeId, true);
        if (jdbc.queryForList("SELECT id FROM resume_attachments WHERE resume_id = ? FOR UPDATE", Long.class, resumeId).size() >= MAX_FILES) {
            throw badRequest("첨부파일은 최대 5개까지 추가할 수 있습니다.");
        }
        ValidatedFile validated = validate(file);
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO resume_attachments (resume_id, filename, content_type, byte_size, content) VALUES (?, ?, ?, ?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, resumeId);
            statement.setString(2, validated.filename());
            statement.setString(3, validated.type());
            statement.setInt(4, validated.bytes().length);
            statement.setBytes(5, validated.bytes());
            return statement;
        }, keys);
        return new Attachment(java.util.Objects.requireNonNull(keys.getKey()).longValue(),
                validated.filename(), validated.type(), validated.bytes().length);
    }

    @Transactional
    public void delete(String subject, Long resumeId, Long attachmentId) {
        requireOwner(subject, resumeId, true);
        if (jdbc.update("DELETE FROM resume_attachments WHERE resume_id = ? AND id = ?", resumeId, attachmentId) != 1) {
            throw notFound();
        }
    }

    @Transactional(readOnly = true)
    public Download download(String subject, Long resumeId, Long attachmentId) {
        requireOwner(subject, resumeId, false);
        return bytes(resumeId, attachmentId);
    }

    @Transactional(readOnly = true)
    public List<Attachment> listForCompany(String subject, Long companyId, Long resumeId) {
        companyResumes.get(subject, companyId, resumeId);
        return metadata(resumeId);
    }

    @Transactional(readOnly = true)
    public Download downloadForCompany(String subject, Long companyId, Long resumeId, Long attachmentId) {
        companyResumes.get(subject, companyId, resumeId);
        return bytes(resumeId, attachmentId);
    }

    private void requireOwner(String subject, Long resumeId, boolean lock) {
        Long userId = members.requireCurrentMember(subject).getId();
        var ids = jdbc.queryForList("SELECT id FROM resumes WHERE id = ? AND user_id = ?" + (lock ? " FOR UPDATE" : ""),
                Long.class, resumeId, userId);
        if (ids.isEmpty()) throw notFound();
    }

    private List<Attachment> metadata(Long resumeId) {
        return jdbc.query("SELECT id, filename, content_type, byte_size FROM resume_attachments WHERE resume_id = ? ORDER BY id",
                (row, index) -> new Attachment(row.getLong("id"), row.getString("filename"),
                        row.getString("content_type"), row.getInt("byte_size")), resumeId);
    }

    private Download bytes(Long resumeId, Long attachmentId) {
        return jdbc.query("SELECT filename, content_type, content FROM resume_attachments WHERE resume_id = ? AND id = ?",
                (row, index) -> new Download(row.getString("filename"), row.getString("content_type"), row.getBytes("content")),
                resumeId, attachmentId).stream().findFirst().orElseThrow(ResumeAttachmentService::notFound);
    }

    static ValidatedFile validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) throw badRequest("파일당 5MB 이하의 파일을 선택해 주세요.");
        String original = file.getOriginalFilename();
        if (original == null) throw badRequest("파일 이름을 확인해 주세요.");
        String filename = original.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (filename.isBlank() || filename.length() > 255) throw badRequest("파일 이름은 255자 이내여야 합니다.");
        String lower = filename.toLowerCase(Locale.ROOT);
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length == 0 || bytes.length > MAX_BYTES) throw badRequest("파일당 5MB 이하의 파일을 선택해 주세요.");
            String type;
            if (lower.endsWith(".pdf") && new String(bytes, 0, Math.min(5, bytes.length), StandardCharsets.US_ASCII).equals("%PDF-")) {
                String tail = new String(bytes, Math.max(0, bytes.length - 1024), Math.min(1024, bytes.length), StandardCharsets.US_ASCII);
                if (!tail.contains("%%EOF")) throw badRequest("올바른 PDF 파일이 아닙니다.");
                type = "application/pdf";
            } else {
                try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                    var readers = ImageIO.getImageReaders(input);
                    if (!readers.hasNext()) throw badRequest("PDF, PNG, JPG 파일만 첨부할 수 있습니다.");
                    var reader = readers.next();
                    try {
                        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                        boolean png = format.equals("png") && lower.endsWith(".png");
                        boolean jpeg = format.equals("jpeg") && (lower.endsWith(".jpg") || lower.endsWith(".jpeg"));
                        if (!png && !jpeg) throw badRequest("PDF, PNG, JPG 파일만 첨부할 수 있습니다.");
                        reader.setInput(input);
                        if (reader.getWidth(0) <= 0 || reader.getHeight(0) <= 0
                                || (long) reader.getWidth(0) * reader.getHeight(0) > 20_000_000L
                                || reader.read(0) == null) throw badRequest("이미지 파일을 확인해 주세요.");
                        type = png ? "image/png" : "image/jpeg";
                    } finally { reader.dispose(); }
                }
            }
            return new ValidatedFile(filename, type, bytes);
        } catch (IOException error) {
            throw badRequest("파일을 읽을 수 없습니다.");
        }
    }

    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "첨부파일 또는 이력서를 찾을 수 없습니다."); }
    private static ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    public record Attachment(Long id, String filename, String contentType, int size) {}
    public record Download(String filename, String contentType, byte[] content) {}
    record ValidatedFile(String filename, String type, byte[] bytes) {}
}
