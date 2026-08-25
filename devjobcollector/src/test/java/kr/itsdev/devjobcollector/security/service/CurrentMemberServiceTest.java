package kr.itsdev.devjobcollector.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import kr.itsdev.devjobcollector.security.account.PersonalProfile;
import kr.itsdev.devjobcollector.security.account.PersonalProfileRepository;
import kr.itsdev.devjobcollector.security.account.ProfileStatus;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.account.UserAccountRepository;
import kr.itsdev.devjobcollector.security.account.UserAccountStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CurrentMemberServiceTest {
    private UserAccountRepository userRepository;
    private PersonalProfileRepository profileRepository;
    private CurrentMemberService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserAccountRepository.class);
        profileRepository = mock(PersonalProfileRepository.class);
        service = new CurrentMemberService(userRepository, profileRepository);
    }

    @Test
    void returnsActiveMemberAndProfile() {
        UserAccount user = mock(UserAccount.class);
        PersonalProfile profile = mock(PersonalProfile.class);
        when(user.getId()).thenReturn(42L);
        when(user.getEmail()).thenReturn("member@example.com");
        when(user.getName()).thenReturn("에어로플릿");
        when(user.getRole()).thenReturn("USER");
        when(user.getStatus()).thenReturn(UserAccountStatus.ACTIVE);
        when(profile.getProfileStatus()).thenReturn(ProfileStatus.PRIVATE);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(profileRepository.findById(42L)).thenReturn(Optional.of(profile));

        var response = service.getCurrentMember("42");

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.name()).isEqualTo("에어로플릿");
        assertThat(response.profileStatus()).isEqualTo("PRIVATE");
    }

    @Test
    void rejectsMalformedSubject() {
        assertUnauthorized(() -> service.getCurrentMember("not-a-number"));
        verify(userRepository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void rejectsInactiveAccount() {
        UserAccount user = mock(UserAccount.class);
        when(user.getStatus()).thenReturn(UserAccountStatus.PENDING_EMAIL);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        assertUnauthorized(() -> service.getCurrentMember("42"));
        verify(profileRepository, never()).findById(42L);
    }

    @Test
    void rejectsDeletedProfile() {
        UserAccount user = mock(UserAccount.class);
        PersonalProfile profile = mock(PersonalProfile.class);
        when(user.getStatus()).thenReturn(UserAccountStatus.ACTIVE);
        when(profile.getProfileStatus()).thenReturn(ProfileStatus.DELETED);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(profileRepository.findById(42L)).thenReturn(Optional.of(profile));

        assertUnauthorized(() -> service.getCurrentMember("42"));
    }

    private void assertUnauthorized(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
