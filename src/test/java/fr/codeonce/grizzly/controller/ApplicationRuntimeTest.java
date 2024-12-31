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

import fr.codeonce.grizzly.runtime.rest.CoreRequestController;
import fr.codeonce.grizzly.runtime.service.cache.CouchConnector;
import fr.codeonce.grizzly.runtime.service.cache.ElasticConnector;
import fr.codeonce.grizzly.runtime.service.cache.MarklogicConnector;
import fr.codeonce.grizzly.runtime.service.cache.MongoConnector;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;


@ContextConfiguration(classes = {CoreRequestController.class, TestConfiguration.class})
public class ApplicationRuntimeTest {

    @Autowired
    WebApplicationContext wac;

    @Autowired
    MockHttpSession session;

    MockMvc mockMvc;

    @Mock
    MongoConnector mongoConnector;

    @Mock
    ElasticConnector elasticConnector;

    @Mock
    CouchConnector couchConnector;

    @Mock
    MarklogicConnector marklogicConnector;

    @InjectMocks
    CoreRequestController coreRequestController;

}
