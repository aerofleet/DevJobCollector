package kr.itsdev.devjobcollector.collection.discovery;

import kr.itsdev.devjobcollector.company.CompanyRepository;
import kr.itsdev.devjobcollector.company.CompanyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Existing verified company records are seeds, not evidence of current hiring or ATS ownership. */
@Service
public class ExistingCompanySeedProvider {
    private final CompanyRepository companies;

    public ExistingCompanySeedProvider(CompanyRepository companies) {
        this.companies = companies;
    }

    @Transactional(readOnly = true)
    public Page<CompanySeed> findSeeds(Pageable page) {
        return companies.findDiscoverySeeds(CompanyStatus.VERIFIED, page)
                .map(c -> new CompanySeed(c.getId(), c.getDisplayName(), c.getWebsiteUrl()));
    }

    public record CompanySeed(Long companyId, String companyName, String homepageUrl) { }
}
