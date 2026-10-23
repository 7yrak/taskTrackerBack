package com.tasktracker.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;

@Configuration @Profile("kova")
public class KovaClient {
    @Bean ClientRegistrationRepository kovaRegistration(
            @Value("${spring.security.oauth2.client.provider.kova.authorization-uri}") String authorize,
            @Value("${spring.security.oauth2.client.provider.kova.token-uri}") String token,
            @Value("${spring.security.oauth2.client.provider.kova.jwk-set-uri}") String jwks,
            @Value("${spring.security.oauth2.client.provider.kova.issuer-uri}") String issuer,
            @Value("${spring.security.oauth2.client.registration.kova.redirect-uri}") String redirect) {
        // Explicit endpoints avoid discovery through the interactive Authelia gateway.
        var registration=ClientRegistration.withRegistrationId("kova").clientId("tasktracker-pilot")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(redirect).scope("openid","profile")
            .authorizationUri(authorize).tokenUri(token).jwkSetUri(jwks).issuerUri(issuer)
            .userNameAttributeName("sub").clientName("Cuenta KOVA").build();
        return new InMemoryClientRegistrationRepository(registration);
    }
}
