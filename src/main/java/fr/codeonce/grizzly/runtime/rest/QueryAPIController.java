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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.mongodb.MongoWriteException;
import fr.codeonce.grizzly.common.runtime.Response;
import fr.codeonce.grizzly.common.runtime.RuntimeQueryRequest;
import fr.codeonce.grizzly.runtime.service.query.QueryHandler;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StopWatch;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = {"*"})
public class QueryAPIController {

    private static final Logger log = LoggerFactory.getLogger(QueryAPIController.class);
    @Autowired
    private QueryHandler queryHandler;

    @RequestMapping(method = {RequestMethod.POST, RequestMethod.GET, RequestMethod.PUT,
            RequestMethod.DELETE}, path = "/runtime/{containerId}")
    public ResponseEntity<Object> executeHelathCheck(@PathVariable String containerId,
                                                     HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException, ParseException {
        try {
            Map<String, String> body = new HashMap<>();
            body.put("status", "ok");
            return ResponseEntity.ok().body(body);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @RequestMapping(method = {RequestMethod.POST, RequestMethod.GET, RequestMethod.PUT,
            RequestMethod.DELETE}, path = "/runtime/query/{containerId}/**")
    public ResponseEntity<Object> executeAllQuery(@PathVariable String containerId,
                                                  HttpServletRequest req, HttpServletResponse res) throws Exception {
        log.debug("controller runtime : {}", containerId);
        Object result = null;
        StopWatch stopWatch = new StopWatch();
        stopWatch.start("handle Query");
        try {
        String runtimeQueryString = req.getHeader("query");
        RuntimeQueryRequest runtimeReq = null;

            runtimeReq = new ObjectMapper().readValue(runtimeQueryString, RuntimeQueryRequest.class);


        StopWatch queryHandlingWatch = new StopWatch();
            queryHandlingWatch.start();
            result = this.queryHandler.handleQuery(runtimeReq, containerId, req, res);
            queryHandlingWatch.stop();
            log.info("query timing {}", queryHandlingWatch.getLastTaskTimeMillis());

        stopWatch.stop();
        log.debug(stopWatch.prettyPrint());
        final Object response = result;

        if (result instanceof Response response1) {
            if (response1.getHttpCode() != 0) {
                return ResponseEntity.status(response1.getHttpCode())
                        .body("{\"customException\":\"" + response1.getResponse() + "\"}");
            } else {
                return ResponseEntity.badRequest()
                        .body("{\"exception\":\"" + response1.getResponse() + "\"}");
            }
        }
        if (!runtimeReq.getReturnType().equals(MediaType.APPLICATION_XML_VALUE)) {
            return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(result);
        } else {
            return ResponseEntity.ok(result);
        }
        } catch (Exception e) {
                throw e;
        }
    }

}
