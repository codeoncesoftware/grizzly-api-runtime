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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.codeonce.grizzly.common.runtime.Response;
import fr.codeonce.grizzly.runtime.service.query.handlers.QueryUtils;
import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SqlFunctionHandler {

    @Autowired
    private QueryUtils queryUtils;

    public Object sqlInResponseHandler(Response result) {
        if (result.getType().equalsIgnoreCase("valid")) {

            return result.getResponse();

        }

        return result;
    }

    public Object sqlOutResponseHandler(Response result) {
        if (result.getType().equals("valid")) {

            JSONArray jsonArray = null;
            try {
                if (result.getResponse().toString() instanceof String
                        && result.getResponse().toString().startsWith("{")) {
                    return Document.parse(result.getResponse().toString());
                } else {
                    List<Document> response = new ArrayList<Document>();
                    JSONObject jsonObject = new JSONObject("{\"list\": " + result.getResponse().toString() + "}");
                    jsonArray = jsonObject.getJSONArray("list");
                    for (int i = 0; i < jsonArray.length(); i++) {
                        response.add(Document.parse(jsonArray.getJSONObject(i).toString()));
                    }
                    return response;
                }
            } catch (Exception e) {
                if (jsonArray != null && jsonArray.length() > 0) {

                    ObjectMapper mapper = new ObjectMapper();

                    try {
                        List<?> participantJsonList = mapper.readValue(result.getResponse().toString(),
                                new TypeReference<List<?>>() {
                                });
                        return participantJsonList;
                    } catch (JsonMappingException e1) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                    } catch (JsonProcessingException e1) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                    }

                }
                // TODO Auto-generated catch block
                return result.getResponse();
            }
        }
        return result;
    }

}
