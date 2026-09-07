package kr.itsdev.devjobcollector.dto.company;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kr.itsdev.devjobcollector.company.CompanyVerificationMethod;

public record CompanyVerificationSubmitRequest(
        @NotNull CompanyVerificationMethod method,
        @NotBlank @Size(max = 500)
        @Pattern(regexp = "^(?!/)(?!.*\\.\\.)(?!.*://).+$") String evidenceObjectKey
) {
    @Override
    public String toString() {
        return "CompanyVerificationSubmitRequest[method=" + method
                + ", evidenceObjectKey=<redacted>]";
    }
}
