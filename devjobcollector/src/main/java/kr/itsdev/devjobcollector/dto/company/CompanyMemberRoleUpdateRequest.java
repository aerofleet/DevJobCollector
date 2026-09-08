package kr.itsdev.devjobcollector.dto.company;

import jakarta.validation.constraints.NotNull;
import kr.itsdev.devjobcollector.company.CompanyMemberRole;

public record CompanyMemberRoleUpdateRequest(@NotNull CompanyMemberRole role) {
}
