package kr.itsdev.devjobcollector.admin.companies;

import java.time.LocalDateTime;
import kr.itsdev.devjobcollector.company.Company;
import kr.itsdev.devjobcollector.company.CompanyRepository;
import kr.itsdev.devjobcollector.company.CompanyStatus;
import kr.itsdev.devjobcollector.company.CompanyVerificationRequest;
import kr.itsdev.devjobcollector.company.CompanyVerificationRequestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminCompanyService {
    private final CompanyRepository companies;
    private final CompanyVerificationRequestRepository requests;

    public AdminCompanyService(CompanyRepository companies,
                               CompanyVerificationRequestRepository requests) {
        this.companies = companies;
        this.requests = requests;
    }

    @Transactional(readOnly = true)
    public Page<CompanyView> list(String keyword, CompanyStatus status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PAGE");
        }
        String term = keyword == null || keyword.isBlank() ? null : keyword.trim();
        if (term != null && term.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_KEYWORD");
        }
        return companies.searchForAdmin(term, status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")))
                .map(CompanyView::from);
    }

    @Transactional(readOnly = true)
    public CompanyDetail detail(Long id) {
        Company company = companies.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND"));
        VerificationView latest = requests.findTopByCompany_IdOrderByRequestedAtDescIdDesc(id)
                .map(VerificationView::from).orElse(null);
        return new CompanyDetail(CompanyView.from(company), latest);
    }

    public record CompanyView(Long id, String legalName, String displayName,
                              String businessNumberMasked, String websiteUrl,
                              String status, long version, LocalDateTime createdAt) {
        static CompanyView from(Company company) {
            return new CompanyView(company.getId(), company.getLegalName(),
                    company.getDisplayName(), company.getBusinessNumberMasked(),
                    company.getWebsiteUrl(), company.getStatus().name(),
                    company.getVersion(), company.getCreatedAt());
        }
    }

    public record CompanyDetail(CompanyView company, VerificationView latestRequest) {}

    public record VerificationView(Long id, String method, String status,
                                   Long requestedById, LocalDateTime requestedAt,
                                   LocalDateTime reviewedAt, String rejectionReason) {
        static VerificationView from(CompanyVerificationRequest request) {
            return new VerificationView(request.getId(), request.getMethod().name(),
                    request.getStatus().name(), request.getRequestedBy().getId(),
                    request.getRequestedAt(), request.getReviewedAt(),
                    request.getRejectionReason());
        }
    }
}
