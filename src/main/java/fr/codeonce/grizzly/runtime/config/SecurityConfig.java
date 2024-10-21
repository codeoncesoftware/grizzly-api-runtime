/*
 * Copyright © 2020 CodeOnce Software (https://www.codeonce.fr/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.codeonce.grizzly.runtime.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@Profile({"!test"})
@EnableMethodSecurity(
        securedEnabled = true,
        jsr250Enabled = true)
public class SecurityConfig{

    private static List<String> clients = Arrays.asList(
            "google",
            "github",
            "facebook",
            "linkedin",
            "gitlab"
    );

    @Autowired
    private Environment env;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(withDefaults())
                .authorizeHttpRequests(authorize ->
                        authorize.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                .requestMatchers("/swagger-ui/**").permitAll()
                                .requestMatchers("/swagger-ui.html").permitAll()
                                .requestMatchers("/v3/api-docs/**").permitAll()
                                .requestMatchers("/actuator/**").permitAll()
                )
                .oauth2Login(oauth2 ->
                        oauth2
                                .tokenEndpoint(token ->
                                        token.accessTokenResponseClient(
                                                authorizationCodeTokenResponseClient()
                                        )
                                )
                                .authorizationEndpoint(autho ->
                                        autho.authorizationRequestResolver(
                                                new CustomAuthorizationRequestResolver(
                                                        clientRegistrationRepository(),
                                                        "/login/oauth2/authorization"
                                                )
                                        )
                                )
                );

        return http.build();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web ->
                web.ignoring().requestMatchers("/runtime/**").requestMatchers("/");
    }

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        List<ClientRegistration> registrations = clients
                .stream()
                .map(this::getRegistration)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return new InMemoryClientRegistrationRepository(registrations);
    }

    private ClientRegistration getRegistration(String client) {
        String clientId = env.getProperty(
                "spring.security.oauth2.client.registration." + client + ".client-id"
        );
        if (clientId == null) {
            return null;
        }

        String clientSecret = env.getProperty(
                "spring.security.oauth2.client.registration." + client + ".client-secret"
        );

        if (client.equals("google")) {
            return CommonOAuth2Provider.GOOGLE
                    .getBuilder(client)
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .build();
        }
        if (client.equals("github")) {
            return CommonOAuth2Provider.GITHUB
                    .getBuilder(client)
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .build();
        }
        if (client.equals("facebook")) {
            return CommonOAuth2Provider.FACEBOOK
                    .getBuilder(client)
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .build();
        }
        if (client.equals("linkedin")) {
            ClientRegistration.Builder builder = ClientRegistration.withRegistrationId(
                    "linkedin"
            );
            builder.clientAuthenticationMethod(
                    ClientAuthenticationMethod.CLIENT_SECRET_POST
            );
            builder.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE);
            builder.redirectUri("{baseUrl}/{action}/oauth2/code/{registrationId}");
            builder.scope("r_emailaddress", "r_liteprofile");
            builder.authorizationUri(
                    "https://www.linkedin.com/oauth/v2/authorization"
            );
            builder.tokenUri("https://www.linkedin.com/oauth/v2/accessToken");
            builder.userInfoUri("https://api.linkedin.com/v2/me");
            builder.userNameAttributeName("id");
            builder.clientName("Linkedin");
            builder.clientId(clientId);
            builder.clientSecret(clientSecret);
            return builder.build();
        }
        if (client.equals("gitlab")) {
            ClientRegistration.Builder builder = ClientRegistration.withRegistrationId(
                    "gitlab"
            );
            builder.clientAuthenticationMethod(
                    ClientAuthenticationMethod.CLIENT_SECRET_POST
            );
            builder.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE);
            builder.redirectUri("{baseUrl}/{action}/oauth2/code/{registrationId}");
            builder.scope("read_user");
            builder.authorizationUri("https://gitlab.com/oauth/authorize");
            builder.tokenUri("https://gitlab.com/oauth/token");
            builder.userInfoUri("https://gitlab.com/api/v4/user");
            builder.userNameAttributeName("username");
            builder.clientName("Gitlab");
            builder.jwkSetUri("https://gitlab.com/oauth/discovery/keys");
            builder.clientId(clientId);
            builder.clientSecret(clientSecret);
            return builder.build();
        }
        return null;
    }

    private OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> authorizationCodeTokenResponseClient() {
        OAuth2AccessTokenResponseHttpMessageConverter tokenResponseHttpMessageConverter = new OAuth2AccessTokenResponseHttpMessageConverter();
        tokenResponseHttpMessageConverter.setAccessTokenResponseConverter(
                new OAuth2AccessTokenResponseConverterWithDefaults()
        );
        RestTemplate restTemplate = new RestTemplate(
                Arrays.asList(
                        new FormHttpMessageConverter(),
                        tokenResponseHttpMessageConverter
                )
        );
        restTemplate.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
        DefaultAuthorizationCodeTokenResponseClient tokenResponseClient = new DefaultAuthorizationCodeTokenResponseClient();
        tokenResponseClient.setRestOperations(restTemplate);

        return tokenResponseClient;
    }
}
