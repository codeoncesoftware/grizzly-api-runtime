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
package fr.codeonce.grizzly.runtime.service.query.handlers;

import com.nimbusds.jose.jwk.RSAKey;
import fr.codeonce.grizzly.common.runtime.RuntimeQueryRequest;
import fr.codeonce.grizzly.common.runtime.SecurityApiConfig;
import fr.codeonce.grizzly.runtime.service.feign.FeignDiscovery;
import fr.codeonce.grizzly.runtime.service.query.authentication.SignUpHandler;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.jwt.JwtHelper;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

@Service
public class OauthHandler {

    private static Logger log = LoggerFactory.getLogger(OauthHandler.class);

    @Value("${frontUrl}")
    private String grizzlyFrontUrl;

    @Autowired
    private FeignDiscovery feignDiscovery;

    @Autowired
    SignUpHandler signUpHandler;

    private final RedirectStrategy authorizationRedirectStrategy = new DefaultRedirectStrategy();

    @Autowired
    private Environment env;

    @SuppressWarnings("unchecked")
    public Object createToken(String username, List<String> roles, String authMS, Map<String, Object> outputMsg)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        long now = (new Date()).getTime();
        Date validity;
        validity = new Date(now + (3600 * 1000)); // to be checked

        String containerId = authMS.substring(StringUtils.ordinalIndexOf(authMS, "/", 4) + 1);
        SecurityApiConfig securityApiConfig = feignDiscovery.getSecurity(containerId);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(
                Base64.getDecoder().decode(securityApiConfig.getPrivateKey().getBytes()));
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
        String token = Jwts.builder()//
                .setSubject(username)//
                .setHeader((Map<String, Object>) Jwts.header().setType("JWT"))//
                .claim("auth", roles)//
                .claim("email_verified", Boolean.parseBoolean(outputMsg.get("email_verified").toString()))//
                .claim("given_name", outputMsg.get("given_name"))//
                .claim("family_name", outputMsg.get("familyName"))//
                .claim("email", outputMsg.get("email"))//
                .claim("identityProvider", "Keycloak")//
                .signWith(privateKey)//
                .setExpiration(validity)//
                .compact();
        return new Document("token", token);

    }

    public Object handleAuthorization(HttpServletRequest req, HttpServletResponse res, RuntimeQueryRequest queryRequest,
                                      String containerId) throws ParseException, IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        List<String> roles = new ArrayList<>();
        JSONObject json = parser(queryRequest.getParsedQuery());
        String username = json.get("username").toString();
        Map<String, Object> outputMsg = feignDiscovery.authorizationEndpoint(username, json.get("password").toString(),
                containerId);
        if (outputMsg.get("authenticationMsg").equals("Authentication succeeded")) {
            if (outputMsg.get("roles") != null) {
                roles = Arrays
                        .asList(outputMsg.get("roles").toString().replace("[", "").replace("]", "")
                                .replace(" ", "").split(","));
            }
            return createToken(username, roles, req.getHeader("authMSRuntimeUrl"), outputMsg);
        } else if (outputMsg.get("authenticationMsg").equals("Check your keycloak server !")) {
            log.info("keycloak server exception");
            res.resetBuffer();
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setHeader("Content-Type", "application/json");
            res.getOutputStream().print("{\"errorMessage\":\"Check your keycloak server !\"}");
            res.flushBuffer();
            return null;

        } else {
            log.info("keycloak creds exception");
            res.resetBuffer();
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setHeader("Content-Type", "application/json");
            res.getOutputStream().print("{\"errorMessage\":\"Check your username and password !\"}");
            res.flushBuffer();
            return null;
        }
    }

    public Object handleGoogleAuthorization(HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        String url = req.getRequestURL().toString().substring(0,
                StringUtils.ordinalIndexOf(req.getRequestURL().toString(), "/", 3));
        String frontUrl = grizzlyFrontUrl + "/runtime/frontRedirect";
        if (req.getAttribute("redirect_uri") != null) {
            frontUrl = req.getAttribute("redirect_uri").toString();
        }
        authorizationRedirectStrategy.sendRedirect(req, res,
                url + "/oauth2/authorization/google?" + req.getHeader("authMSRuntimeUrl") + "&" + frontUrl + "&" +
                        env.getProperty("spring.security.oauth2.client.registration.google.redirect-uri") + "&" +
                        "google");
        return null;
    }

    public Object handleGithubAuthorization(HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        String url = req.getRequestURL().toString().substring(0,
                StringUtils.ordinalIndexOf(req.getRequestURL().toString(), "/", 3));
        String frontUrl = grizzlyFrontUrl + "/runtime/frontRedirect";
        if (req.getAttribute("redirect_uri") != null) {
            frontUrl = req.getAttribute("redirect_uri").toString();
        }
        authorizationRedirectStrategy.sendRedirect(req, res,
                url + "/oauth2/authorization/github?" + req.getHeader("authMSRuntimeUrl") + "&" + frontUrl + "&" +
                        env.getProperty("spring.security.oauth2.client.registration.github.redirect-uri") + "&" +
                        "github");
        return null;
    }

    public Object handleFacebookAuthorization(HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        String url = req.getRequestURL().toString().substring(0,
                StringUtils.ordinalIndexOf(req.getRequestURL().toString(), "/", 3));
        String frontUrl = grizzlyFrontUrl + "/runtime/frontRedirect";
        if (req.getAttribute("redirect_uri") != null) {
            frontUrl = req.getAttribute("redirect_uri").toString();
        }
        authorizationRedirectStrategy.sendRedirect(req, res,
                url + "/oauth2/authorization/facebook?" + req.getHeader("authMSRuntimeUrl") + "&" + frontUrl + "&" +
                        env.getProperty("spring.security.oauth2.client.registration.facebook.redirect-uri") + "&" +
                        "facebook");
        return null;
    }

    public Object handleLinkedinAuthorization(HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        String url = req.getRequestURL().toString().substring(0,
                StringUtils.ordinalIndexOf(req.getRequestURL().toString(), "/", 3));
        String frontUrl = grizzlyFrontUrl + "/runtime/frontRedirect";
        if (req.getAttribute("redirect_uri") != null) {
            frontUrl = req.getAttribute("redirect_uri").toString();
        }
        authorizationRedirectStrategy.sendRedirect(req, res,
                url + "/oauth2/authorization/linkedin?" + req.getHeader("authMSRuntimeUrl") + "&" + frontUrl + "&" +
                        env.getProperty("spring.security.oauth2.client.registration.linkedin.redirect-uri") + "&" +
                        "linkedin");
        return null;
    }

    public Object handleGitlabAuthorization(HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        String url = req.getRequestURL().toString().substring(0,
                StringUtils.ordinalIndexOf(req.getRequestURL().toString(), "/", 3));
        String frontUrl = grizzlyFrontUrl + "/runtime/frontRedirect";
        if (req.getAttribute("redirect_uri") != null) {
            frontUrl = req.getAttribute("redirect_uri").toString();
        }
        authorizationRedirectStrategy.sendRedirect(req, res,
                url + "/oauth2/authorization/gitlab?" + req.getHeader("authMSRuntimeUrl") + "&" + frontUrl + "&" +
                        env.getProperty("spring.security.oauth2.client.registration.gitlab.redirect-uri") + "&" +
                        "gitlab");
        return null;
    }

    public Object handleUserinfo(HttpServletResponse res, String token, String idp) throws IOException, ParseException {
        Map<String, Object> msg = new HashMap<>();
        JSONObject tokenInfo = parser(JwtHelper.decode(token).getClaims());
        if (idp.equalsIgnoreCase("KEYCLOAK")) {
            msg = keycloakUserinfoEndpoint(tokenInfo);

        }
        if (idp.equalsIgnoreCase("GOOGLE")) {
            msg = googleUserinfoEndpoint(tokenInfo);
        }
        if (idp.equalsIgnoreCase("GITHUB")) {
            msg = githubUserinfoEndpoint(tokenInfo);
        }
        if (idp.equalsIgnoreCase("FACEBOOK")) {
            msg = facebookUserinfoEndpoint(tokenInfo);
        }
        if (idp.equalsIgnoreCase("LINKEDIN")) {
            msg = linkedinUserinfoEndpoint(tokenInfo);
        }
        if (idp.equalsIgnoreCase("GITLAB")) {
            msg = gitlabUserinfoEndpoint(tokenInfo);
        }
        if (msg.get("identityProviderError") != null) {
            res.resetBuffer();
            res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            res.setHeader("Content-Type", "application/json");
            res.flushBuffer();
        }
        return msg;
    }

    private Map<String, Object> gitlabUserinfoEndpoint(JSONObject tokenInfo) {
        Map<String, Object> output = new HashMap<>();
        if (tokenInfo.get("identityProvider").equals("Gitlab")) {
            output = gitlabUserInformation(tokenInfo);
        } else {
            output.put("identityProviderError", "Please check the name of your identity provider");
        }
        return output;
    }

    private Map<String, Object> gitlabUserInformation(JSONObject tokenInfo) {
        Map<String, Object> output = new HashMap<>();

        output.put("identity_provider", tokenInfo.get("identityProvider"));
        output.put("roles", tokenInfo.get("auth"));
        output.put("username", tokenInfo.get("sub"));
        output.put("email", tokenInfo.get("email").toString());
        output.put("two_factor_enabled", tokenInfo.get("two_factor_enabled"));
        output.put("avatar_url", tokenInfo.get("avatar_url"));
        output.put("authentication_microservice", tokenInfo.get("authentication_microservice"));
        output.put("web_url", tokenInfo.get("web_url"));
        output.put("job_title", tokenInfo.get("job_title"));
        output.put("name", tokenInfo.get("name"));

        if (tokenInfo.get("organization") != null) {
            output.put("organization", tokenInfo.get("organization").toString());
        }
        if (tokenInfo.get("bio") != null && !tokenInfo.get("bio").equals("")) {
            output.put("bio", tokenInfo.get("bio").toString());
        }
        if (tokenInfo.get("state") != null && !tokenInfo.get("state").equals("")) {
            output.put("state", tokenInfo.get("state").toString());
        }
        return output;
    }

    private Map<String, Object> linkedinUserinfoEndpoint(JSONObject tokenInfo) {
        Map<String, Object> output = new HashMap<>();
        if (tokenInfo.get("identityProvider").equals("Linkedin")) {
            output.put("identity_provider", tokenInfo.get("identityProvider"));
            output.put("roles", tokenInfo.get("auth"));
            output.put("username", tokenInfo.get("sub"));
            output.put("lastName", tokenInfo.get("LastName"));
            output.put("firstName", tokenInfo.get("FirstName"));
            output.put("authentication_microservice", tokenInfo.get("authentication_microservice"));
        } else {
            output.put("identityProviderError", "Please check the name of your identity provider");
        }
        return output;
    }

    private Map<String, Object> facebookUserinfoEndpoint(JSONObject tokenInfo) {
        Map<String, Object> output = new HashMap<>();
        if (tokenInfo.get("identityProvider").equals("Facebook")) {
            output.put("identity_provider", tokenInfo.get("identityProvider"));
            output.put("roles", tokenInfo.get("auth"));
            output.put("username", tokenInfo.get("sub"));
            output.put("email", tokenInfo.get("email"));
            output.put("authentication_microservice", tokenInfo.get("authentication_microservice"));
        } else {
            output.put("identityProviderError", "Please check the name of your identity provider");
        }
        return output;
    }

    private Map<String, Object> githubUserinfoEndpoint(JSONObject tokenInfo) {
        Map<String, Object> output = new HashMap<>();
        if (tokenInfo.get("identityProvider").equals("Github")) {
            output = githubUserInformation(tokenInfo);
        } else {
            output.put("identityProviderError", "Please check the name of your identity provider");
        }
        return output;
    }

    private Map<String, Object> githubUserInformation(JSONObject tokenInfo) {
        Map<String, Object> output = new HashMap<>();

        output.put("identity_provider", tokenInfo.get("identityProvider"));
        output.put("roles", tokenInfo.get("auth"));
        output.put("username", tokenInfo.get("sub"));
        output.put("repos_url", tokenInfo.get("repos_url"));
        output.put("two_factor_authentication", tokenInfo.get("two_factor_authentication"));
        output.put("url", tokenInfo.get("url"));
        output.put("avatar_url", tokenInfo.get("avatar_url"));
        output.put("authentication_microservice", tokenInfo.get("authentication_microservice"));
        if (tokenInfo.get("bio") != null) {
            output.put("bio", tokenInfo.get("bio").toString());
        }
        if (tokenInfo.get("company") != null) {
            output.put("company", tokenInfo.get("company").toString());
        }
        if (tokenInfo.get("email") != null) {
            output.put("email", tokenInfo.get("email").toString());
        }
        if (tokenInfo.get("name") != null) {
            output.put("name", tokenInfo.get("name").toString());
        }
        return output;
    }

    private Map<String, Object> keycloakUserinfoEndpoint(JSONObject tokenInfo) throws ParseException {
        Map<String, Object> output = new HashMap<>();
        if (tokenInfo.get("identityProvider").equals("Keycloak")) {
            output = userInformation(tokenInfo);
        } else {
            output.put("identityProviderError", "Please check the name of your identity provider");
        }
        return output;
    }

    private Map<String, Object> userInformation(JSONObject tokenInfo) {
        Map<String, Object> output = new HashMap<>();

        output.put("identity_provider", tokenInfo.get("identityProvider"));
        output.put("roles", tokenInfo.get("auth"));
        output.put("username", tokenInfo.get("sub"));
        output.put("email_verified", tokenInfo.get("email_verified"));
        output.put("authentication_microservice", tokenInfo.get("authentication_microservice"));
        if (tokenInfo.get("given_name") != null) {
            output.put("given_name", tokenInfo.get("given_name").toString());
        }
        if (tokenInfo.get("family_name") != null) {
            output.put("family_name", tokenInfo.get("family_name").toString());
        }
        if (tokenInfo.get("email") != null) {
            output.put("email", tokenInfo.get("email").toString());
        }
        return output;
    }

    private Map<String, Object> googleUserinfoEndpoint(JSONObject tokenInfo) {
        Map<String, Object> output = new HashMap<>();
        if (tokenInfo.get("identityProvider").equals("Google")) {
            output = getUserInformation(tokenInfo);
        } else {
            output.put("identityProviderError", "Please check the name of your identity provider");
        }
        return output;
    }

    private Map<String, Object> getUserInformation(JSONObject tokenInfo) {
        Map<String, Object> output = new HashMap<>();

        output.put("email", tokenInfo.get("email"));
        output.put("email_verified", tokenInfo.get("email_verified"));
        output.put("full_name", tokenInfo.get("full_name"));
        output.put("given_name", tokenInfo.get("given_name"));
        output.put("profile_picture", tokenInfo.get("profile_picture"));
        output.put("authentication_microservice", tokenInfo.get("authentication_microservice"));
        if (tokenInfo.get("phone_number") != null) {
            output.put("phone", tokenInfo.get("phone_number"));
        }
        if (tokenInfo.get("gender") != null) {
            output.put("gender", tokenInfo.get("gender"));
        }
        if (tokenInfo.get("sub") != null) {
            output.put("username", tokenInfo.get("sub"));
        }
        return output;

    }

    public Object handleJWKuri(String containerId) throws NoSuchAlgorithmException, InvalidKeySpecException {
        SecurityApiConfig securityApiConfig = feignDiscovery.getSecurity(containerId);
        final X509EncodedKeySpec keySpecs = new X509EncodedKeySpec(
                Base64.getDecoder().decode(securityApiConfig.getPublicKey().getBytes()));
        final KeyFactory kf = KeyFactory.getInstance("RSA");
        final RSAPublicKey publicKey = (RSAPublicKey) kf.generatePublic(keySpecs);
        RSAKey jwk = new RSAKey.Builder(publicKey).build();
        return jwk.toJSONObject();

    }

    public List<String> handlegetRoles(String containerId) {
        return feignDiscovery.getRoles(containerId);
    }

    public JSONObject parser(String value) throws ParseException {
        JSONParser parser = new JSONParser();
        return (JSONObject) parser.parse(value);
    }
}
