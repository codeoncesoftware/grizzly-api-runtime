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
package fr.codeonce.grizzly.runtime.service.feign;

import fr.codeonce.grizzly.common.runtime.SecurityApiConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "grizzly-api-core", url = "${core-url}")
public interface FeignDiscovery {

    @GetMapping("/api/resource/public/getResource")
    public byte[] getResource(@RequestParam("containerId") String containerId,
                              @RequestParam("resourcePath") String resourcePath);

    @GetMapping("/api/resource/public/getResourceWithId")
    public byte[] getResourceFileWithId(@RequestParam("containerId") String containerId,
                                        @RequestParam("fileId") String fileId);

    @GetMapping("/api/dbsource/public/getdbsource")
    public DBSource getDBSource(@RequestParam("dbsourceId") String dbsourceId);

    @GetMapping("/api/container/public/get")
    public Map<String, String> getContainerInfos(@RequestParam("containerId") String containerId);

    @GetMapping("/api/container/public/getroles")
    public List<String> getRoles(@RequestParam("containerId") String containerId);

    @GetMapping("/api/container/public/project")
    public String getDatabaseFromProject(@RequestParam("containerId") String containerId);

    @GetMapping("/api/container/public/security")
    public SecurityApiConfig getSecurity(@RequestParam("containerId") String containerId);

    @GetMapping("/api/user/info")
    public Map<String, String> getUserAttributs(@RequestParam("username") String username);

    @PostMapping("/api/container/public/endpoint/authorization")
    public Map<String, Object> authorizationEndpoint(@RequestParam("username") String username, @RequestParam("password") String password, @RequestParam("containerId") String containerId);

    @GetMapping("/api/container/public/endpoint/googleAuth")
    public Map<String, Object> googleAuthorizationEndpoint(@RequestParam("containerId") String containerId);

}
