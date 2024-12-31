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

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.codeonce.grizzly.common.runtime.Response;
import fr.codeonce.grizzly.common.runtime.RuntimeQueryRequest;
import fr.codeonce.grizzly.runtime.service.cache.ElasticConnector;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
public class ElasticHandler {

    @Autowired
    private QueryUtils queryUtils;

    @Autowired
    private ElasticConnector elasticConnector;

    private static final Logger log = LoggerFactory.getLogger(ElasticHandler.class);

    public Object handleFindQuery(RuntimeQueryRequest queryRequest, Object query, HttpServletRequest req,
                                  HttpServletResponse res) throws Exception {

        if (ObjectUtils.isEmpty(queryRequest.getCollectionName())) {
            throw new IllegalStateException("Collection name can't be empty");
        }

        if (query instanceof Response) {
            return query;
        }
        try {
            Request request = new Request("GET", "/" + queryRequest.getCollectionName() + "/_search");
            if (query == null) {
                request.setJsonEntity(
                        queryUtils.parseQuery(queryRequest, queryRequest.getQuery(), null, null, req, null).get("query"));
            } else {
                request.setJsonEntity((String) query);
            }

            if (queryRequest.getOutFunctions() != null && !queryRequest.getOutFunctions().isEmpty()) {
                return executeQuery(queryRequest, request);
            } else {
                ObjectMapper objectMapper = new ObjectMapper();
                return objectMapper.readValue((String) executeQuery(queryRequest, request), Map.class);

            }
        } catch (IOException e) {
            log.warn("Error while executing the fetch request : {}", e.getCause());
            throw new IllegalArgumentException("Bad Fetch Request");
        }
    }

    public Object handleInsertQuery(RuntimeQueryRequest queryRequest, String parsedBody, HttpServletRequest req,
                                    HttpServletResponse res, Object finalResult) {
        try {
            if (finalResult instanceof Response) {
                return finalResult;
            }
            HttpEntity entity = null;
            UUID uuid = UUID.randomUUID();
            if (finalResult != null) {
                entity = new NStringEntity((String) finalResult, ContentType.APPLICATION_JSON);
            } else {
                entity = new NStringEntity(parsedBody, ContentType.APPLICATION_JSON);

            }
            Request request = new Request("PUT",
                    "/" + queryRequest.getCollectionName() + "/" + queryRequest.getIndexType() + "/" + uuid.toString());
            request.setEntity(entity);
            if (queryRequest.getOutFunctions() != null && !queryRequest.getOutFunctions().isEmpty()) {
                return executeQuery(queryRequest, request);
            } else {
                ObjectMapper objectMapper = new ObjectMapper();
                return objectMapper.readValue((String) executeQuery(queryRequest, request), Map.class);

            }

        } catch (Exception e) {
            log.warn("Error while executing the Insert request : {}", e.getCause());
            throw new IllegalArgumentException("Bad Insert Request");
        }
    }

    public void handleDeleteQuery(RuntimeQueryRequest queryRequest, HttpServletRequest req) throws Exception {
        try {
            Request request = new Request("POST", "/" + queryRequest.getCollectionName() + "/_delete_by_query");
            request.setJsonEntity(
                    queryUtils.parseQuery(queryRequest, queryRequest.getQuery(), null, null, req, null).get("query"));
            executeQuery(queryRequest, request);
        } catch (IOException e) {
            log.warn("Error while executing the Delete request : {}", e.getCause());
            throw new IllegalArgumentException("Bad Delete Request");
        }

    }

    private Object executeQuery(RuntimeQueryRequest queryRequest, Request request) throws IOException {
        RestClient restClient = this.elasticConnector.getRestClient(queryRequest.getDbsourceId());
        org.elasticsearch.client.Response indexResponse = restClient.performRequest(request);
        return EntityUtils.toString(indexResponse.getEntity());
    }

}
