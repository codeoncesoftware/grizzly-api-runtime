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

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

public class CustomAuthorizationRequestResolver
        implements OAuth2AuthorizationRequestResolver {

    private OAuth2AuthorizationRequestResolver defaultResolver;

    public CustomAuthorizationRequestResolver(
            ClientRegistrationRepository repo, String authorizationRequestBaseUri) {
        defaultResolver = new DefaultOAuth2AuthorizationRequestResolver(repo, authorizationRequestBaseUri);
    }

    private OAuth2AuthorizationRequest customizeAuthorizationRequest(OAuth2AuthorizationRequest req,
                                                                     HttpServletRequest request) {
        OAuth2AuthorizationRequest oAuth2AuthorizationRequest = null;
        if (req != null && request.getAttribute("authMSRuntimeUrl") != null
                && request.getAttribute("frontUrl") != null) {
            oAuth2AuthorizationRequest = OAuth2AuthorizationRequest
                    .from(req)
                    .state("{\"authMSRuntimeUrl\":\"" + request.getAttribute("authMSRuntimeUrl").toString()
                            + "\", \"client\":\""
                            + request.getAttribute("client")
                            + "\", \"frontUrl\":\""
                            + request.getAttribute("frontUrl").toString() + "\"}")
                    .redirectUri(request.getAttribute("redirectUrl").toString()).build();
        }
        return oAuth2AuthorizationRequest;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        if (request.getRequestURI().equals("/oauth2/authorization/google")
                || request.getRequestURI().equals("/oauth2/authorization/github")
                || request.getRequestURI().equals("/oauth2/authorization/facebook")
                || request.getRequestURI().equals("/oauth2/authorization/linkedin")
                || request.getRequestURI().equals("/oauth2/authorization/gitlab")) {
            String[] externalParmas = request.getQueryString().split("&");
            request.setAttribute("authMSRuntimeUrl", externalParmas[0]);
            request.setAttribute("frontUrl", externalParmas[1]);
            request.setAttribute("redirectUrl", externalParmas[2]);
            request.setAttribute("client", externalParmas[3]);
            OAuth2AuthorizationRequest req = defaultResolver.resolve(request, externalParmas[3]);
            req = customizeAuthorizationRequest(req, request);
            return req;
        }

        return null;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return null;
    }

}
