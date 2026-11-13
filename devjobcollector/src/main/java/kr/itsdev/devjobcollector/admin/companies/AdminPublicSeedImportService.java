package kr.itsdev.devjobcollector.admin.companies;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import kr.itsdev.devjobcollector.admin.AdminAuditLog;
import kr.itsdev.devjobcollector.admin.AdminAuditLogRepository;
import kr.itsdev.devjobcollector.admin.AdminAuditResult;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.collection.discovery.SmeJobCompanySeedImporter;
import kr.itsdev.devjobcollector.collection.discovery.SmeJobCompanySeedReader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminPublicSeedImportService {
    private static final long MAX_BYTES = 10_000_000;
    private final SmeJobCompanySeedImporter importer;
    private final AdminAuditLogRepository audit;

    public AdminPublicSeedImportService(SmeJobCompanySeedImporter importer, AdminAuditLogRepository audit) {
        this.importer = importer;
        this.audit = audit;
    }

    @Transactional(rollbackFor = Exception.class)
    public SmeJobCompanySeedReader.Counts importFile(MultipartFile file, String encoding, AdminPrincipal actor,
                                                      String requestId, String ip, String userAgent) {
        if (actor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ADMIN_AUTH_REQUIRED");
        if (actor.role() == AdminRole.REVIEWER) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN_ROLE_DENIED");
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_SEED_FILE");
        }
        Charset charset = switch (encoding == null ? "" : encoding) {
            case "UTF-8" -> StandardCharsets.UTF_8;
            case "CP949" -> Charset.forName("MS949");
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_SEED_ENCODING");
        };
        try (var input = new InputStreamReader(file.getInputStream(), charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))) {
            var counts = importer.importCsv(input);
            audit.save(AdminAuditLog.record(actor.id(), "PUBLIC_COMPANY_SEEDS_IMPORTED", "PUBLIC_COMPANY_SEED",
                    null, null, null, "{\"acceptedRows\":" + counts.accepted() + ",\"skippedRows\":"
                            + counts.skipped() + "}", AdminAuditResult.SUCCESS, requestId,
                    truncate(ip, 45), truncate(userAgent, 500)));
            return counts;
        } catch (IOException | UncheckedIOException | IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_SEED_CSV", invalid);
        }
    }

    private static String truncate(String value, int limit) {
        return value == null || value.length() <= limit ? value : value.substring(0, limit);
    }
}
