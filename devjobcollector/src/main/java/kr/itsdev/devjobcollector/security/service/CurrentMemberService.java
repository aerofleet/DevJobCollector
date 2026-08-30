package kr.itsdev.devjobcollector.security.service;

import kr.itsdev.devjobcollector.dto.auth.MemberMeResponse;
import kr.itsdev.devjobcollector.security.account.PersonalProfile;
import kr.itsdev.devjobcollector.security.account.PersonalProfileRepository;
import kr.itsdev.devjobcollector.security.account.ProfileStatus;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.account.UserAccountRepository;
import kr.itsdev.devjobcollector.security.account.UserAccountStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentMemberService {
    private final UserAccountRepository userRepository;
    private final PersonalProfileRepository profileRepository;

    public CurrentMemberService(
            UserAccountRepository userRepository,
            PersonalProfileRepository profileRepository
    ) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public MemberMeResponse getCurrentMember(String subject) {
        UserAccount user = requireCurrentMember(subject);
        PersonalProfile profile = profileRepository.findById(user.getId())
                .orElseThrow(() -> unauthorized("MEMBER_PROFILE_INACTIVE"));

        return new MemberMeResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                profile.getProfileStatus().name()
        );
    }

    @Transactional(readOnly = true)
    public UserAccount requireCurrentMember(String subject) {
        Long userId = parseUserId(subject);
        UserAccount user = userRepository.findById(userId)
                .filter(account -> account.getStatus() == UserAccountStatus.ACTIVE)
                .orElseThrow(() -> unauthorized("MEMBER_ACCOUNT_INACTIVE"));
        profileRepository.findById(userId)
                .filter(candidate -> candidate.getProfileStatus() != ProfileStatus.DELETED)
                .orElseThrow(() -> unauthorized("MEMBER_PROFILE_INACTIVE"));
        return user;
    }

    private Long parseUserId(String subject) {
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException | NullPointerException exception) {
            throw unauthorized("MEMBER_SUBJECT_INVALID");
        }
    }

    private static MemberAuthenticationException unauthorized(String errorCode) {
        return new MemberAuthenticationException(errorCode);
    }
}
