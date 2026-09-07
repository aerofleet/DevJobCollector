package kr.itsdev.devjobcollector.dto.company;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompanySignupRequest(
        @NotBlank @Size(max = 200) String legalName,
        @NotBlank @Size(max = 150) String displayName,
        @NotBlank @Pattern(regexp = "\\d{3}-?\\d{2}-?\\d{5}") String businessNumber,
        @Size(max = 500) @Pattern(regexp = "^$|https?://\\S+$") String websiteUrl
) {
    @Override
    public String toString() {
        return "CompanySignupRequest[legalName=" + legalName
                + ", displayName=" + displayName
                + ", businessNumber=<redacted>"
                + ", websiteUrl=" + websiteUrl + "]";
    }
}
