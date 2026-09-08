package kr.itsdev.devjobcollector.company;

import java.util.EnumSet;
import java.util.Set;

public enum CompanyPermission {
    VIEW_COMPANY(CompanyMemberRole.OWNER, CompanyMemberRole.ADMIN,
            CompanyMemberRole.RECRUITER, CompanyMemberRole.VIEWER),
    EDIT_COMPANY(CompanyMemberRole.OWNER, CompanyMemberRole.ADMIN),
    VIEW_MEMBERS(CompanyMemberRole.OWNER, CompanyMemberRole.ADMIN,
            CompanyMemberRole.RECRUITER, CompanyMemberRole.VIEWER),
    INVITE_MEMBERS(CompanyMemberRole.OWNER, CompanyMemberRole.ADMIN),
    ASSIGN_ADMIN(CompanyMemberRole.OWNER),
    ASSIGN_RECRUITER(CompanyMemberRole.OWNER, CompanyMemberRole.ADMIN),
    REMOVE_MEMBER(CompanyMemberRole.OWNER, CompanyMemberRole.ADMIN),
    TRANSFER_OWNERSHIP(CompanyMemberRole.OWNER),
    CREATE_JOB_POST(CompanyMemberRole.OWNER, CompanyMemberRole.ADMIN,
            CompanyMemberRole.RECRUITER),
    EDIT_JOB_POST(CompanyMemberRole.OWNER, CompanyMemberRole.ADMIN,
            CompanyMemberRole.RECRUITER);

    private final Set<CompanyMemberRole> allowedRoles;

    CompanyPermission(CompanyMemberRole firstRole, CompanyMemberRole... remainingRoles) {
        this.allowedRoles = EnumSet.of(firstRole, remainingRoles);
    }

    boolean allows(CompanyMemberRole role) {
        return allowedRoles.contains(role);
    }
}
