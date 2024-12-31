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
package fr.codeonce.grizzly.runtime.service.iamAuthorization;

import fr.codeonce.grizzly.runtime.service.feign.FeignDiscovery;
import io.jsonwebtoken.Jwts;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.security.PrivateKey;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class IAMAuthorizationService {

    private static final String IDENTITY_PROVIDER = "identityProvider";
    private static final String AUTHMSRUNTIMEURL = "authMSRuntimeUrl";

    private String googleAccessTokenUri = "https://www.googleapis.com/oauth2/v4/token";
    private String googleUserInfoUri = "https://www.googleapis.com/oauth2/v3/userinfo";
    private String facebookAccessTokenUri = "https://graph.facebook.com/v2.8/oauth/access_token";
    private String facebookUserInfoUri = "https://graph.facebook.com/me?fields=id,name,email";
    private String githubAccessTokenUri = "https://github.com/login/oauth/access_token";
    private String githubUserInfoUri = "https://api.github.com/user";
    private String gitlabAccessTokenUri = "https://gitlab.com/oauth/token";
    private String gitlabUserInfoUri = "https://gitlab.com/api/v4/user";
    private String linkedinAccessTokenUri = "https://www.linkedin.com/oauth/v2/accessToken";
    private String linkedinUserInfoUri = "https://api.linkedin.com/v2/me";

    @Lazy
    @Autowired
    ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    FeignDiscovery feignDiscovery;

    @Value("${frontUrl}")
    private String frontUrl;


    @SuppressWarnings("unchecked")
    public String loginWithLinkedin(RestTemplate restTemplate, Date validity, List<String> defaultRole,
                                    Map<String, String> allRequestParams, PrivateKey privateKey, JSONObject externalParmas) {
        ClientRegistration linkedinClient = clientRegistrationRepository.findByRegistrationId("linkedin");
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();

        map.add("client_id", linkedinClient.getClientId());
        map.add("client_secret", linkedinClient.getClientSecret());
        map.add("code", allRequestParams.get("code"));
        map.add("grant_type", "authorization_code");
        map.add("redirect_uri", frontUrl + "/runtime/iam/oauth2");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", MediaType.ALL_VALUE);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(linkedinClient.getClientId(), linkedinClient.getClientSecret());
        HttpEntity entity = new HttpEntity(map, headers);

        Map<String, String> linkedinTokenUri = restTemplate.postForObject(linkedinAccessTokenUri, entity, Map.class);
        Map<String, String> userinfo = new HashMap<>();
        if (linkedinTokenUri != null) {
            userinfo = restTemplate
                    .getForObject(
                            linkedinUserInfoUri + "?oauth2_access_token=" + linkedinTokenUri.get("access_token"),
                            Map.class);

        }
        return Jwts.builder()
                .setSubject(userinfo.get("localizedLastName") + userinfo.get("localizedFirstName"))
                .setHeader((Map<String, Object>) Jwts.header().setType("JWT"))
                .claim("LastName", userinfo.get("localizedLastName"))//
                .claim("FirstName", userinfo.get("localizedFirstName"))//
                .claim("authentication_microservice", externalParmas.get(AUTHMSRUNTIMEURL).toString())//
                .claim("auth", defaultRole)//
                .claim(IDENTITY_PROVIDER, "Linkedin")//
                .signWith(privateKey).setExpiration(validity).compact();
    }

    @SuppressWarnings("unchecked")
    public String loginWithGitlab(RestTemplate restTemplate, Date validity, List<String> defaultRole,
                                  Map<String, String> args, PrivateKey privateKey, JSONObject externalParmas) {
        ClientRegistration gitlabClient = clientRegistrationRepository.findByRegistrationId("gitlab");
        args.put("client_id", gitlabClient.getClientId());
        args.put("client_secret", gitlabClient.getClientSecret());
        Map<String, String> gitlabTokenUri = restTemplate.postForObject(gitlabAccessTokenUri, args, Map.class);
        Map<String, String> userinfo = new HashMap<>();
        if (gitlabTokenUri != null) {
            userinfo = restTemplate
                    .getForObject(gitlabUserInfoUri + "?access_token=" + gitlabTokenUri.get("access_token"), Map.class);
        }
        return Jwts.builder().setSubject(userinfo.get("username").toString())
                .setHeader((Map<String, Object>) Jwts.header().setType("JWT"))
                .claim("name", userinfo.get("name"))//
                .claim("state", userinfo.get("state"))//
                .claim("avatar_url", userinfo.get("avatar_url"))//
                .claim("web_url", userinfo.get("web_url"))//
                .claim("bio", userinfo.get("bio"))//
                .claim("organization", userinfo.get("organization"))//
                .claim("job_title", userinfo.get("job_title"))//
                .claim("two_factor_enabled", userinfo.get("two_factor_enabled"))//
                .claim("email", userinfo.get("email"))//
                .claim("authentication_microservice", externalParmas.get(AUTHMSRUNTIMEURL).toString())//
                .claim("auth", defaultRole)//
                .claim(IDENTITY_PROVIDER, "Gitlab")//
                .signWith(privateKey).setExpiration(validity).compact();
    }

    @SuppressWarnings("unchecked")
    public String loginWithGithub(RestTemplate restTemplate, Date validity, List<String> defaultRole,
                                  Map<String, String> args, PrivateKey privateKey, JSONObject externalParmas) {
        ClientRegistration githubClient = clientRegistrationRepository.findByRegistrationId("github");
        args.put("client_id", githubClient.getClientId());
        args.put("client_secret", githubClient.getClientSecret());

        Map<String, String> githubTokenUri = restTemplate.postForObject(githubAccessTokenUri, args, Map.class);
        Map<String, String> oauthUser = new HashMap<>();
        if (githubTokenUri != null) {
            WebClient client = WebClient.create();
            oauthUser = client.get()
                    .uri(githubUserInfoUri)
                    .header("Authorization", "token " + githubTokenUri.get("access_token"))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        }
        return Jwts.builder().setSubject(oauthUser.get("login").toString())
                .setHeader((Map<String, Object>) Jwts.header().setType("JWT"))
                .claim("gists_url", oauthUser.get("gists_url"))//
                .claim("two_factor_authentication", oauthUser.get("two_factor_authentication"))//
                .claim("repos_url", oauthUser.get("repos_url"))//
                .claim("subscriptions_url", oauthUser.get("subscriptions_url"))//
                .claim("collaborators", oauthUser.get("collaborators"))//
                .claim("avatar_url", oauthUser.get("avatar_url"))//
                .claim("auth", defaultRole)//
                .claim("url", oauthUser.get("url"))//
                .claim("bio", oauthUser.get("bio"))//
                .claim("company", oauthUser.get("company"))//
                .claim("email", oauthUser.get("email"))//
                .claim("name", oauthUser.get("name"))//
                .claim("authentication_microservice", externalParmas.get(AUTHMSRUNTIMEURL).toString())//
                .claim(IDENTITY_PROVIDER, "Github")//
                .signWith(privateKey).setExpiration(validity).compact();
    }

    @SuppressWarnings("unchecked")
    public String loginWithFacebook(RestTemplate restTemplate, Date validity, List<String> defaultRole,
                                    Map<String, String> args, PrivateKey privateKey, JSONObject externalParmas) {
        ClientRegistration facebookClient = clientRegistrationRepository.findByRegistrationId("facebook");
        args.put("client_id", facebookClient.getClientId());
        args.put("client_secret", facebookClient.getClientSecret());
        Map<String, String> facebookTokenUri = restTemplate.postForObject(facebookAccessTokenUri, args, Map.class);
        Map<String, String> facebookUserinfo = new HashMap<>();
        if (facebookTokenUri != null) {
            facebookUserinfo = restTemplate
                    .getForObject(facebookUserInfoUri + "&access_token=" + facebookTokenUri.get("access_token"),
                            Map.class);
        }
        return Jwts.builder().setSubject(facebookUserinfo.get("name"))
                .setHeader((Map<String, Object>) Jwts.header().setType("JWT"))
                .claim("email", facebookUserinfo.get("email"))//
                .claim("authentication_microservice", externalParmas.get(AUTHMSRUNTIMEURL).toString())//
                .claim("auth", defaultRole)//
                .claim(IDENTITY_PROVIDER, "Facebook")//
                .signWith(privateKey).setExpiration(validity).compact();
    }

    @SuppressWarnings("unchecked")
    public String loginWithGoogle(RestTemplate restTemplate, Date validity, List<String> defaultRole,
                                  Map<String, String> args, PrivateKey privateKey, JSONObject externalParmas) {
        ClientRegistration googleClient = clientRegistrationRepository.findByRegistrationId("google");
        args.put("client_id", googleClient.getClientId());
        args.put("client_secret", googleClient.getClientSecret());
        Map<String, String> googleTokenUri = restTemplate.postForObject(googleAccessTokenUri, args, Map.class);
        Map<String, String> googleUserinfo = new HashMap<>();
        if (googleTokenUri != null) {
            googleUserinfo = restTemplate
                    .getForObject(googleUserInfoUri + "?access_token=" + googleTokenUri.get("access_token"), Map.class);
        }
        return Jwts.builder().setSubject(googleUserinfo.get("name"))
                .setHeader((Map<String, Object>) Jwts.header().setType("JWT"))
                .claim("email", googleUserinfo.get("email"))//
                .claim("email_verified", googleUserinfo.get("email_verified"))//
                .claim("full_name", googleUserinfo.get("name"))//
                .claim("given_name", googleUserinfo.get("given_name"))//
                .claim("family_name", googleUserinfo.get("family_name"))//
                .claim("profile_picture", googleUserinfo.get("picture"))//
                .claim("gender", googleUserinfo.get("gender"))//
                .claim("auth", defaultRole)//
                .claim("phone_number", googleUserinfo.get("phone_number"))//
                .claim("authentication_microservice", externalParmas.get(AUTHMSRUNTIMEURL).toString())//
                .claim(IDENTITY_PROVIDER, "Google")//
                .signWith(privateKey).setExpiration(validity).compact();
    }

}
