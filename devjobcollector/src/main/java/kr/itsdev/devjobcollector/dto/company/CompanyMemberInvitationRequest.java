package kr.itsdev.devjobcollector.dto.company;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.itsdev.devjobcollector.company.CompanyMemberRole;

public record CompanyMemberInvitationRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotNull CompanyMemberRole role
) {
    @Override
    public String toString() {
        return "CompanyMemberInvitationRequest[email=<redacted>, role=" + role + "]";
    }
}
