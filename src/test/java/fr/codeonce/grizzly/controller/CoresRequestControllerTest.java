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
package fr.codeonce.grizzly.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.Charset;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@WebAppConfiguration
public class CoresRequestControllerTest extends ApplicationRuntimeTest {

    private static final String DBSOURCE_ID = "dbsourceId";

    private static final String PROVIDER = "provider";

    private Document requestBody = new Document();

    private final ObjectMapper mapper = new ObjectMapper();

    public static final MediaType APPLICATION_JSON_UTF8 = new MediaType(MediaType.APPLICATION_JSON.getType(),
            MediaType.APPLICATION_JSON.getSubtype(), Charset.forName("utf8"));

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(coreRequestController).build();
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldUpdateMongoCacheRequestById() throws Exception {

        requestBody.clear();
        requestBody.append(DBSOURCE_ID, "mongoId");
        requestBody.append(PROVIDER, "MONGO");

        String requestJson = mapper.writeValueAsString(requestBody);

        execute(requestJson);

        verify(mongoConnector, times(1)).updateCache("mongoId");
    }

    @Test
    public void shouldUpdateElasticCacheRequestById() throws Exception {

        requestBody.clear();
        requestBody.append(DBSOURCE_ID, "elasticId");
        requestBody.append(PROVIDER, "ELASTICSEARCH");

        String requestJson = mapper.writeValueAsString(requestBody);

        execute(requestJson);

        verify(elasticConnector, times(1)).updateCache("elasticId");
    }

    @Test
    public void shouldUpdateCouchbaseCacheRequestById() throws Exception {

        requestBody.clear();
        requestBody.append(DBSOURCE_ID, "couchId");
        requestBody.append(PROVIDER, "COUCHDB");

        String requestJson = mapper.writeValueAsString(requestBody);

        execute(requestJson);

        verify(couchConnector, times(1)).updateCache("couchId");
    }

    @Test
    public void shouldUpdateMarklogicCacheRequestById() throws Exception {

        requestBody.clear();
        requestBody.append(DBSOURCE_ID, "marklogicId");
        requestBody.append(PROVIDER, "MARKLOGIC");

        String requestJson = mapper.writeValueAsString(requestBody);

        execute(requestJson);

        verify(marklogicConnector, times(1)).updateCache("marklogicId");
    }

    /**
     * Execute PUT Request
     *
     * @param requestJson
     * @throws Exception
     */
    private void execute(String requestJson) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/runtime/cache/update").contentType(APPLICATION_JSON_UTF8)
                .content(requestJson)).andReturn();
    }

}
