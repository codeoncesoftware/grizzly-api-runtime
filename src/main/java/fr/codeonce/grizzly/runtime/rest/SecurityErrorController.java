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

import jakarta.servlet.http.HttpServletResponse;
import org.bson.Document;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SecurityErrorController {

    @RequestMapping(method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.DELETE}, path = "/runtime/error")
    public Object errorRuntime(@RequestHeader String authorizedMessage, @RequestHeader String code, HttpServletResponse res) {
        res.setStatus(Integer.parseInt(code));
        return new Document("message", authorizedMessage);

    }
}
