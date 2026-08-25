package kr.itsdev.devjobcollector.controller;

import kr.itsdev.devjobcollector.dto.auth.MemberMeResponse;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members")
public class MemberController {
    private final CurrentMemberService currentMemberService;

    public MemberController(CurrentMemberService currentMemberService) {
        this.currentMemberService = currentMemberService;
    }

    @GetMapping("/me")
    public MemberMeResponse me(@AuthenticationPrincipal String subject) {
        return currentMemberService.getCurrentMember(subject);
    }
}
