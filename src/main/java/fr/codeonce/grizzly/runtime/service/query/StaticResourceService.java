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
package fr.codeonce.grizzly.runtime.service.query;

import fr.codeonce.grizzly.runtime.service.feign.FeignDiscovery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@Service
public class StaticResourceService {

    @Autowired
    private FeignDiscovery feignDiscovery;

    public byte[] getResource(String containerId, HttpServletRequest request, HttpServletResponse response) {
        String requestURL = request.getRequestURL().toString();
        String path = requestURL.split("/path/")[1];
        return this.feignDiscovery.getResource(containerId, path);
    }

    public byte[] getResourceWithId(String containerId, String fileId) {
        return this.feignDiscovery.getResourceFileWithId(containerId, fileId);
    }

    public void setHttpServletResponse(byte[] resource, HttpServletResponse response) throws IOException {
        InputStream inputStream = null;

        try (OutputStream outputStream = response.getOutputStream()) {

            response.setContentLengthLong(resource.length);

            outputStream.write(resource);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

}
