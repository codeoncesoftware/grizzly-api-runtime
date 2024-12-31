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

import fr.codeonce.grizzly.common.runtime.RuntimeRequest;
import fr.codeonce.grizzly.runtime.service.ContainerContextHolder;
import fr.codeonce.grizzly.runtime.service.freemarker.FreeMarkerService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.net.URL;

@RestController
@CrossOrigin(origins = {"*"}, allowedHeaders = {"*"})
@RequestMapping("/runtime")
public class FreeMarkerController {

    @Autowired
    private FreeMarkerService freeMarkerService;

    @PostMapping("/freemarker")
    public String handleResource(@RequestBody RuntimeRequest<?> request, HttpServletRequest req) throws Exception {

        ContainerContextHolder.setContext(request.getContainerId());

        // base URL
        URL requestURL = new URL(req.getRequestURL().toString());
        String port = requestURL.getPort() == -1 ? "" : ":" + requestURL.getPort();
        String baseUrl = requestURL.getProtocol() + "://" + requestURL.getHost() + port;

        return freeMarkerService.handle(request, baseUrl);

    }
}
