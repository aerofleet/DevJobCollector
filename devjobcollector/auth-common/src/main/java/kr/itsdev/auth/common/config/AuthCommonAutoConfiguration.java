package kr.itsdev.auth.common.config;

import kr.itsdev.auth.common.oauth.CommonOAuth2UserService;
import kr.itsdev.auth.common.oauth.SocialLoginSuccessHandler;
import kr.itsdev.auth.common.spi.SocialUserUpsertService;
import kr.itsdev.auth.common.spi.TokenIssueService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@AutoConfiguration
@EnableConfigurationProperties(AuthCommonProperties.class)
public class AuthCommonAutoConfiguration {

    @Bean
    @ConditionalOnBean(SocialUserUpsertService.class)
    @ConditionalOnMissingBean(name = "commonOAuth2UserService")
    public OAuth2UserService<OAuth2UserRequest, OAuth2User> commonOAuth2UserService(
            SocialUserUpsertService socialUserUpsertService
    ) {
        return new CommonOAuth2UserService(socialUserUpsertService);
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
}
