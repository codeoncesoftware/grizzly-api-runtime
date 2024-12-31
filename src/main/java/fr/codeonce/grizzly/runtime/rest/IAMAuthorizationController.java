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
package fr.codeonce.grizzly.runtime.rest;

import fr.codeonce.grizzly.common.runtime.SecurityApiConfig;
import fr.codeonce.grizzly.runtime.service.feign.FeignDiscovery;
import fr.codeonce.grizzly.runtime.service.iamAuthorization.IAMAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;

@RestController
@CrossOrigin(origins = {"*"})
public class IAMAuthorizationController {

    private static final String TOKEN = "token";
    private static final String CLIENT = "client";

    @Autowired
    FeignDiscovery feignDiscovery;

    @Autowired
    IAMAuthorizationService iamAuthorizationService;

    @Value("${frontUrl}")
    private String frontUrl;

    private final RedirectStrategy authorizationRedirectStrategy = new DefaultRedirectStrategy();

    @RequestMapping(method = {RequestMethod.POST, RequestMethod.GET, RequestMethod.PUT,
            RequestMethod.DELETE}, path = "/runtime/iam/oauth2")
    public Object authorizationController(@RequestParam Map<String, String> allRequestParams,
                                          HttpServletRequest request, HttpServletResponse response)
            throws ParseException, NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        RestTemplate restTemplate = new RestTemplate();
        JSONParser parser = new JSONParser();
        JSONObject externalParmas = (JSONObject) parser.parse(allRequestParams.get("state"));

        Map<String, String> args = new HashMap<>();
        args.put("code", allRequestParams.get("code"));
        args.put("grant_type", "authorization_code");
        args.put("redirect_uri", frontUrl + "/runtime/iam/oauth2");

        PrivateKey privateKey = null;
        String token = "";
        String containerId = externalParmas.get("authMSRuntimeUrl").toString().substring(StringUtils.ordinalIndexOf(
                externalParmas.get("authMSRuntimeUrl").toString(), "/", 4) + 1);

        SecurityApiConfig securityApiConfig = feignDiscovery.getSecurity(containerId);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(
                Base64.getDecoder().decode(securityApiConfig.getPrivateKey().getBytes()));
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        privateKey = keyFactory.generatePrivate(keySpec);

        long now = (new Date()).getTime();
        Date validity = new Date(now + (3600 * 1000)); // to be checked
        List<String> defaultRole = new ArrayList<>();
        defaultRole.add("authenticated user");

        if (externalParmas.get(CLIENT).equals("google")) {
            token = iamAuthorizationService.loginWithGoogle(restTemplate, validity, defaultRole, args, privateKey,
                    externalParmas);
        }
        if (externalParmas.get(CLIENT).equals("facebook")) {
            token = iamAuthorizationService.loginWithFacebook(restTemplate, validity, defaultRole, args, privateKey,
                    externalParmas);
        }
        if (externalParmas.get(CLIENT).equals("github")) {
            token = iamAuthorizationService.loginWithGithub(restTemplate, validity, defaultRole, args, privateKey,
                    externalParmas);
        }
        if (externalParmas.get(CLIENT).equals("gitlab")) {
            token = iamAuthorizationService.loginWithGitlab(restTemplate, validity, defaultRole, args, privateKey,
                    externalParmas);
        }
        if (externalParmas.get(CLIENT).equals("linkedin")) {
            token = iamAuthorizationService.loginWithLinkedin(restTemplate, validity, defaultRole, allRequestParams,
                    privateKey, externalParmas);
        }
        Document grizzlytoken = new Document(TOKEN, token);
        String redirectionUrl = UriComponentsBuilder.fromUriString(externalParmas.get("frontUrl").toString())
                .queryParam(TOKEN, grizzlytoken.get(TOKEN)).build().toUriString();
        authorizationRedirectStrategy.sendRedirect(request, response, redirectionUrl);
        return token;
    }

    @GetMapping("/runtime/frontRedirect")
    public String redirectController(@RequestParam String token) {
        return "Here is your token: " + token;
    }
}
