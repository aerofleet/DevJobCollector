package kr.itsdev.devjobcollector.collection.discovery;

import java.util.List;
import java.util.Objects;
import kr.itsdev.devjobcollector.collection.repository.CompanySourceTargetRepository;
import kr.itsdev.devjobcollector.company.CompanyRepository;
import kr.itsdev.devjobcollector.company.CompanyStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Processes HTML supplied by a trusted caller; it does not retrieve or verify the page itself. */
@Service
public class ExistingCompanyDiscoveryService {
    private static final int MAX_HTML_LENGTH = 2_000_000;
    private final CompanyRepository companies;
    private final CompanySourceTargetRepository targets;
    private final HomepageAtsLinkDiscovery links;
    private final DiscoveryCandidateStore candidates;

    public ExistingCompanyDiscoveryService(CompanyRepository companies, CompanySourceTargetRepository targets,
                                           HomepageAtsLinkDiscovery links, DiscoveryCandidateStore candidates) {
        this.companies = companies;
        this.targets = targets;
        this.links = links;
        this.candidates = candidates;
    }

    @Transactional
    public int process(Long companyId, String pageUrl, String html) {
        if (companyId == null || pageUrl == null || html == null || html.length() > MAX_HTML_LENGTH) {
            throw new IllegalArgumentException("Invalid discovery input");
        }
        var company = companies.findByIdForUpdate(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));
        if (company.getStatus() != CompanyStatus.VERIFIED || company.getWebsiteUrl() == null
                || !Objects.equals(company.getWebsiteUrl(), pageUrl)
                || targets.existsByCompanyId(companyId)) {
            throw new IllegalArgumentException("Company is not eligible for discovery");
        }
        List<HomepageAtsLinkDiscovery.Candidate> found = links.discover(pageUrl, html).stream()
                .filter(candidate -> targets.findByProviderAndSourceIdentifier(
                        candidate.fingerprint().provider(), candidate.fingerprint().sourceIdentifier()).isEmpty())
                .toList();
        candidates.record(companyId, found);
        return found.size();
    }
}
