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
package fr.codeonce.grizzly.runtime.function.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import fr.codeonce.grizzly.common.runtime.Response;
import fr.codeonce.grizzly.common.runtime.RuntimeQueryRequest;
import fr.codeonce.grizzly.runtime.service.cache.ElasticConnector;;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

@Service
public class ElasticFunctionHandler {

    @Autowired
    private ElasticConnector elasticConnector;

    public Object elasticInResponseHandler(Response result) {
        if (result.getType().equalsIgnoreCase("valid")) {
            if (result.getResponse() instanceof String) {
                return result.getResponse();
            } else {
                Response response = new Response();
                response.setHttpCode(400);
                response.setType("exception");
                response.setResponse("invalid object to save");
                return response;
            }
        }

        return result;
    }

    public Object elasticOutResponseHandler(Response result, RuntimeQueryRequest queryRequest) {
        ObjectMapper objectMapper = new ObjectMapper();

        if (result.getType().equalsIgnoreCase("valid")) {

            if (result.getResponse() instanceof String) {

                try {
                    return objectMapper.readValue((String) result.getResponse(), Map.class);
                } catch (JsonProcessingException e) {
                    // TODO Auto-generated catch block
                    return result.getResponse();
                }

            }
            return result.getResponse();

        }
        return result;
    }

    public Object fetchElasticEntity(String entityResponse, RuntimeQueryRequest queryRequest) {

        Map fromJson = new Gson().fromJson(entityResponse, Map.class);
        Request request = new Request("GET", "/" + queryRequest.getCollectionName() + "/_search");
        request.setJsonEntity("{\r\n" + "  \"query\": {\r\n" + "    \"terms\": {\r\n" + "      \"_id\": [ \""
                + fromJson.get("_id") + "\"] \r\n" + "    }\r\n" + "  }\r\n" + "}");
        RestClient restClient = this.elasticConnector.getRestClient(queryRequest.getDbsourceId());
        org.elasticsearch.client.Response indexResponse;
        try {
            indexResponse = restClient.performRequest(request);
            return EntityUtils.toString(indexResponse.getEntity());

        } catch (IOException e) {
            // TODO Auto-generated catch block
            return null;
        }

    }

}
