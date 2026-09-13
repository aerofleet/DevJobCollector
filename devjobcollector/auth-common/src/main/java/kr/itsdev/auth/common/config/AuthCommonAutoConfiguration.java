package kr.itsdev.auth.common.config;

import kr.itsdev.auth.common.oauth.CommonOAuth2UserService;
import kr.itsdev.auth.common.oauth.OAuth2ProfileAdapter;
import kr.itsdev.auth.common.oauth.OAuthProviderRegistry;
import kr.itsdev.auth.common.oauth.SessionOAuth2StateRegistry;
import kr.itsdev.auth.common.oauth.SocialLoginSuccessHandler;
import kr.itsdev.auth.common.oauth.SocialLoginFailureHandler;
import kr.itsdev.auth.common.spi.SocialUserUpsertService;
import kr.itsdev.auth.common.spi.TokenIssueService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

@AutoConfiguration
@EnableConfigurationProperties(AuthCommonProperties.class)
public class AuthCommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OAuthProviderRegistry oauthProviderRegistry(
            ObjectProvider<OAuth2ProfileAdapter> additionalAdapters
    ) {
        return OAuthProviderRegistry.defaults(additionalAdapters.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthorizationRequestRepository<OAuth2AuthorizationRequest> oauth2StateRegistry(
            AuthCommonProperties properties
    ) {
        return new SessionOAuth2StateRegistry(
                properties.getOauthStateTtl(),
                properties.getOauthStateMaxPending()
        );
    }

    @Bean
    @ConditionalOnBean(SocialUserUpsertService.class)
    @ConditionalOnMissingBean(name = "commonOAuth2UserService")
    public OAuth2UserService<OAuth2UserRequest, OAuth2User> commonOAuth2UserService(
            SocialUserUpsertService socialUserUpsertService,
            OAuthProviderRegistry oauthProviderRegistry
    ) {
        return new CommonOAuth2UserService(socialUserUpsertService, oauthProviderRegistry);
    }

    @Bean
    @ConditionalOnBean(TokenIssueService.class)
    @ConditionalOnMissingBean(name = "socialLoginSuccessHandler")
    public AuthenticationSuccessHandler socialLoginSuccessHandler(
            AuthCommonProperties properties,
            TokenIssueService tokenIssueService
    ) {
        return new SocialLoginSuccessHandler(properties, tokenIssueService);
    }

    @Bean
    @ConditionalOnMissingBean(name = "socialLoginFailureHandler")
    public AuthenticationFailureHandler socialLoginFailureHandler(AuthCommonProperties properties) {
        return new SocialLoginFailureHandler(properties);
    }
}
