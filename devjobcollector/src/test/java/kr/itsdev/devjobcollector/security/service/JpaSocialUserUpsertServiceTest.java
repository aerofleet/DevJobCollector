package kr.itsdev.devjobcollector.security.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
}
