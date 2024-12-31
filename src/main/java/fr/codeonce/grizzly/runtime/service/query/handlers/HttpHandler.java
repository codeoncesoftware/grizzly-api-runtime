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

import fr.codeonce.grizzly.common.runtime.RuntimeQueryRequest;
import fr.codeonce.grizzly.common.runtime.resource.RuntimeResourceParameter;
import fr.codeonce.grizzly.runtime.service.query.QueryHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class HttpHandler {

    private static final Logger log = LoggerFactory.getLogger(QueryHandler.class);

    private final RestTemplate restTemplate;

    public String serviceURL;

    public HttpHandler(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    public String handleFindQuery(String servicesURL) {
        return this.restTemplate.getForObject(servicesURL, String.class);

    }

    private Map<String, String> getPathVariables(RuntimeQueryRequest queryRequest, String servletPath) {
        Map<String, String> map = new HashMap<>();
        // 16 is more then length of "/runtime/query/{containerId} to get the path of
        // the API after the ContainerID
        List<String> receivedPath = Arrays
                .asList(servletPath.substring(servletPath.indexOf('/', 16)).substring(1).split("/"));
        List<String> resspath = Arrays.asList(queryRequest.getPath().substring(1).split("/"));

        int index = 0;
        for (String part : resspath) {
            if (part.contains("{")) {
                map.put(part.replace("{", "").replace("}", ""), receivedPath.get(index));
            }
            index++;
        }
        return map;
    }

    public void handleDeleteQuery(RuntimeQueryRequest queryRequest, HttpServletRequest req) {
        this.serviceURL = queryRequest.getServiceURL();
        Map<String, String> paths = getPathVariables(queryRequest, req.getServletPath());

        // get headers parameters

        Map<String, List<String>> headersParamsMap = Collections.list(req.getHeaderNames()).stream()
                .collect(Collectors.toMap(Function.identity(), h -> Collections.list(req.getHeaders(h))));
        List<RuntimeResourceParameter> endpointParameters = queryRequest.getParameters();
        if (endpointParameters != null && !endpointParameters.isEmpty()) {
            endpointParameters.stream().forEach(param -> {
                try {

                    // manage the intersection of mapping between path and targets
                    if (queryRequest.getMapping().stream()
                            .filter(el -> el.getPath().substring(1).equals(param.getName())).findFirst().get().getType()
                            .equals("query") && param.getIn().equalsIgnoreCase("query")) {
                        this.serviceURL += "?" + queryRequest.getMapping().stream()
                                .filter(el -> el.getPath().substring(1).equals(param.getName())).findFirst().get()
                                .getTarget() + "=" + req.getParameter(param.getName());
                    }
                    if (queryRequest.getMapping().stream()
                            .filter(el -> el.getPath().substring(1).equals(param.getName())).findFirst().get().getType()
                            .equals("query") && param.getIn().equalsIgnoreCase("path")) {
                        this.serviceURL += "?" + queryRequest.getMapping().stream()
                                .filter(el -> el.getPath().substring(1).equals(param.getName())).findFirst().get()
                                .getTarget() + "=" + paths.get(param.getName());
                    }
                    if (queryRequest.getMapping().stream()
                            .filter(el -> el.getPath().substring(1).equals(param.getName())).findFirst().get().getType()
                            .equals("path") && param.getIn().equalsIgnoreCase("query")) {
                        paths.put(param.getName(), req.getParameter(param.getName()));

                    }
                } catch (Exception e) {
                    throw new RuntimeException("" + e.getMessage());
                }
            });
        }

        this.restTemplate.delete(this.serviceURL, paths);

    }
}
