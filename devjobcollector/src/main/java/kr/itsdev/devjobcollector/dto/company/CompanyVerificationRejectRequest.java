package kr.itsdev.devjobcollector.dto.company;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompanyVerificationRejectRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
