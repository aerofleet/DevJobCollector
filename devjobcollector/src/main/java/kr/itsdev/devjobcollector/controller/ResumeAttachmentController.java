package kr.itsdev.devjobcollector.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import kr.itsdev.devjobcollector.career.ResumeAttachmentService;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ResumeAttachmentController {
    private final ResumeAttachmentService service;
    public ResumeAttachmentController(ResumeAttachmentService service) { this.service = service; }

    @GetMapping("/api/v1/members/me/resumes/{resumeId}/attachments")
    public List<ResumeAttachmentService.Attachment> list(@AuthenticationPrincipal String subject, @PathVariable Long resumeId) {
        return service.list(subject, resumeId);
    }

    @PostMapping(value = "/api/v1/members/me/resumes/{resumeId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResumeAttachmentService.Attachment> upload(@AuthenticationPrincipal String subject,
            @PathVariable Long resumeId, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upload(subject, resumeId, file));
    }

    @DeleteMapping("/api/v1/members/me/resumes/{resumeId}/attachments/{attachmentId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal String subject, @PathVariable Long resumeId, @PathVariable Long attachmentId) {
        service.delete(subject, resumeId, attachmentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/members/me/resumes/{resumeId}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal String subject, @PathVariable Long resumeId, @PathVariable Long attachmentId) {
        return file(service.download(subject, resumeId, attachmentId));
    }

    @GetMapping("/api/v1/companies/{companyId}/resumes/{resumeId}/attachments")
    public List<ResumeAttachmentService.Attachment> companyList(@AuthenticationPrincipal String subject,
            @PathVariable Long companyId, @PathVariable Long resumeId) {
        return service.listForCompany(subject, companyId, resumeId);
    }

    @GetMapping("/api/v1/companies/{companyId}/resumes/{resumeId}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> companyDownload(@AuthenticationPrincipal String subject,
            @PathVariable Long companyId, @PathVariable Long resumeId, @PathVariable Long attachmentId) {
        return file(service.downloadForCompany(subject, companyId, resumeId, attachmentId));
    }

    private ResponseEntity<byte[]> file(ResumeAttachmentService.Download file) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(file.contentType())).contentLength(file.content().length).body(file.content());
    }
}
