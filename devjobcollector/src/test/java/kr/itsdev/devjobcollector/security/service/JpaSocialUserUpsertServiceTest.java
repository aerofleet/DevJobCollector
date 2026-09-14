package kr.itsdev.devjobcollector.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import kr.itsdev.auth.common.exception.AccountLinkRequiredException;
import kr.itsdev.auth.common.model.SocialProfile;
import kr.itsdev.auth.common.model.SocialProvider;
import kr.itsdev.devjobcollector.security.account.AuthProvider;
import kr.itsdev.devjobcollector.security.account.PersonalProfileRepository;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.account.UserAccountRepository;
import kr.itsdev.devjobcollector.security.account.UserIdentityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

class JpaSocialUserUpsertServiceTest {

    @Test
    void rejectsCaseInsensitiveEmailCollisionBeforeAnyWrite() {
        UserAccountRepository userRepository = mock(UserAccountRepository.class);
        UserIdentityRepository identityRepository = mock(UserIdentityRepository.class);
        PersonalProfileRepository profileRepository = mock(PersonalProfileRepository.class);
        AccountLinkService accountLinkService = mock(AccountLinkService.class);
        JpaSocialUserUpsertService service = new JpaSocialUserUpsertService(
                userRepository, identityRepository, profileRepository, accountLinkService);
        UserAccount existing = UserAccount.activeSocial(
                "existing@example.com", "existing", AuthProvider.LOCAL, null);

        when(identityRepository.findByProviderAndProviderSubject(
                AuthProvider.GOOGLE, "new-subject")).thenReturn(Optional.empty());
        when(accountLinkService.linkIfRequested(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("existing@example.com"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.upsert(new SocialProfile(
                SocialProvider.GOOGLE,
                "new-subject",
                "EXISTING@EXAMPLE.COM",
                "attacker-controlled-name",
                null,
                "https://accounts.google.com",
                true
        ))).isInstanceOf(AccountLinkRequiredException.class);

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(identityRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(profileRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @ParameterizedTest
    @MethodSource("unverifiedKakaoProfiles")
    void rejectsNewKakaoIdentityWithoutVerifiedEmailBeforeAnyWrite(
            String email,
            Boolean emailVerified
    ) {
        UserAccountRepository userRepository = mock(UserAccountRepository.class);
        UserIdentityRepository identityRepository = mock(UserIdentityRepository.class);
        PersonalProfileRepository profileRepository = mock(PersonalProfileRepository.class);
        AccountLinkService accountLinkService = mock(AccountLinkService.class);
        JpaSocialUserUpsertService service = new JpaSocialUserUpsertService(
                userRepository, identityRepository, profileRepository, accountLinkService);

        when(identityRepository.findByProviderAndProviderSubject(
                AuthProvider.KAKAO, "kakao-subject")).thenReturn(Optional.empty());
        when(accountLinkService.linkIfRequested(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsert(new SocialProfile(
                SocialProvider.KAKAO,
                "kakao-subject",
                email,
                "Kakao User",
                null,
                "https://kauth.kakao.com",
                emailVerified
        ))).isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception ->
                org.assertj.core.api.Assertions.assertThat(exception.getError().getErrorCode())
                        .isEqualTo("OAUTH_EMAIL_REQUIRED"));

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(identityRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(profileRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private static java.util.stream.Stream<Arguments> unverifiedKakaoProfiles() {
        return java.util.stream.Stream.of(
                Arguments.of(null, true),
                Arguments.of("kakao@example.com", null),
                Arguments.of("kakao@example.com", false)
        );
    }

    @Test
    void createsNewKakaoIdentityFromVerifiedEmailAndStableSubject() {
        UserAccountRepository userRepository = mock(UserAccountRepository.class);
        UserIdentityRepository identityRepository = mock(UserIdentityRepository.class);
        PersonalProfileRepository profileRepository = mock(PersonalProfileRepository.class);
        AccountLinkService accountLinkService = mock(AccountLinkService.class);
        JpaSocialUserUpsertService service = new JpaSocialUserUpsertService(
                userRepository, identityRepository, profileRepository, accountLinkService);

        when(accountLinkService.linkIfRequested(any())).thenReturn(Optional.empty());
        when(identityRepository.findByProviderAndProviderSubject(
                AuthProvider.KAKAO, "kakao-subject")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("kakao@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(identityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(profileRepository.existsByUser(any())).thenReturn(true);

        var result = service.upsert(new SocialProfile(
                SocialProvider.KAKAO,
                "kakao-subject",
                "KAKAO@EXAMPLE.COM",
                "Kakao User",
                null,
                "https://kauth.kakao.com",
                true
        ));

        assertThat(result.email()).isEqualTo("kakao@example.com");
        verify(identityRepository).findByProviderAndProviderSubject(
                AuthProvider.KAKAO, "kakao-subject");
        verify(identityRepository).save(any());
    }
}
