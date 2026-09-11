package kr.itsdev.devjobcollector.security.hardening;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "security.hardening")
public class SecurityHardeningProperties {
    @Min(1)
    @Max(1440)
    private int windowMinutes = 60;

    @Min(100)
    private int maxTrackedKeys = 10_000;

    @Min(1)
    private int companySignupsPerWindow = 5;

    @Min(1)
    private int memberInvitationsPerWindow = 30;

    @Min(1)
    private int verificationRequestsPerWindow = 5;

    @Min(1)
    private int verificationReviewsPerWindow = 60;

    public int limitFor(SecurityAction action) {
        return switch (action) {
            case COMPANY_SIGNUP -> companySignupsPerWindow;
            case COMPANY_MEMBER_INVITATION -> memberInvitationsPerWindow;
            case COMPANY_VERIFICATION_REQUEST -> verificationRequestsPerWindow;
            case COMPANY_VERIFICATION_REVIEW -> verificationReviewsPerWindow;
        };
    }

    public int getWindowMinutes() { return windowMinutes; }
    public void setWindowMinutes(int value) { this.windowMinutes = value; }
    public int getMaxTrackedKeys() { return maxTrackedKeys; }
    public void setMaxTrackedKeys(int value) { this.maxTrackedKeys = value; }
    public int getCompanySignupsPerWindow() { return companySignupsPerWindow; }
    public void setCompanySignupsPerWindow(int value) { this.companySignupsPerWindow = value; }
    public int getMemberInvitationsPerWindow() { return memberInvitationsPerWindow; }
    public void setMemberInvitationsPerWindow(int value) { this.memberInvitationsPerWindow = value; }
    public int getVerificationRequestsPerWindow() { return verificationRequestsPerWindow; }
    public void setVerificationRequestsPerWindow(int value) { this.verificationRequestsPerWindow = value; }
    public int getVerificationReviewsPerWindow() { return verificationReviewsPerWindow; }
    public void setVerificationReviewsPerWindow(int value) { this.verificationReviewsPerWindow = value; }
}
