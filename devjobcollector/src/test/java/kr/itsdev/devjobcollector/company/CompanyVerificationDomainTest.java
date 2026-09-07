package kr.itsdev.devjobcollector.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import kr.itsdev.devjobcollector.security.account.AuthProvider;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import org.junit.jupiter.api.Test;

class CompanyVerificationDomainTest {
    private final UserAccount owner = UserAccount.activeSocial(
            "owner@example.com", "owner", AuthProvider.GITHUB, "owner-subject");
    private final UserAccount reviewer = UserAccount.activeSocial(
            "admin@example.com", "admin", AuthProvider.GITHUB, "admin-subject");
    private final Company company = Company.pendingVerification(
            "테스트 주식회사", "테스트", "a".repeat(64), "***-**-12345", null, owner);
    private final LocalDateTime requestedAt = LocalDateTime.of(2026, 9, 8, 9, 0);

    @Test
    void approvesPendingRequestWithReviewerAndTimestamp() {
        CompanyVerificationRequest request = pending("verification/1/evidence.pdf");
        LocalDateTime reviewedAt = requestedAt.plusHours(1);

        request.approve(reviewer, reviewedAt);

        assertThat(request.getStatus()).isEqualTo(CompanyVerificationStatus.APPROVED);
        assertThat(request.getReviewedBy()).isSameAs(reviewer);
        assertThat(request.getReviewedAt()).isEqualTo(reviewedAt);
        assertThat(request.getRejectionReason()).isNull();
    }

    @Test
    void rejectsPendingRequestWithTrimmedReason() {
        CompanyVerificationRequest request = pending("verification/1/evidence.pdf");

        request.reject(reviewer, "  서류 식별 불가  ", requestedAt.plusHours(1));

        assertThat(request.getStatus()).isEqualTo(CompanyVerificationStatus.REJECTED);
        assertThat(request.getRejectionReason()).isEqualTo("서류 식별 불가");
    }

    @Test
    void rejectsUrlTraversalAndSecondReview() {
        assertThatThrownBy(() -> pending("https://storage.example/evidence.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pending("verification/../secret.pdf"))
                .isInstanceOf(IllegalArgumentException.class);

        CompanyVerificationRequest request = pending("verification/1/evidence.pdf");
        request.approve(reviewer, requestedAt.plusHours(1));
        assertThatThrownBy(() -> request.reject(reviewer, "재심사", requestedAt.plusHours(2)))
                .isInstanceOf(CompanyVerificationException.class)
                .hasMessage("COMPANY_VERIFICATION_ALREADY_REVIEWED");
    }

    private CompanyVerificationRequest pending(String key) {
        return CompanyVerificationRequest.pending(company, owner,
                CompanyVerificationMethod.BUSINESS_REGISTRATION_DOCUMENT, key, requestedAt);
    }
}
