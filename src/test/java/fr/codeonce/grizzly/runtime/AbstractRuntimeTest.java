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
package fr.codeonce.grizzly.runtime;

import com.mongodb.client.MongoClient;
import fr.codeonce.grizzly.runtime.service.cache.CouchConnector;
import fr.codeonce.grizzly.runtime.service.cache.ElasticConnector;
import fr.codeonce.grizzly.runtime.service.cache.MongoConnector;
import fr.codeonce.grizzly.runtime.service.feign.FeignDiscovery;
import fr.codeonce.grizzly.runtime.service.feign.FeignDiscoveryFunction;
import fr.codeonce.grizzly.runtime.service.schematron.SchematronRuntimeService;
import fr.codeonce.grizzly.runtime.test.query.xml.ClassPathResolverConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(classes = ApplicationRuntimeTest.class)
@ContextConfiguration(classes = {ClassPathResolverConfiguration.class})
@ActiveProfiles({"test"})
public abstract class AbstractRuntimeTest {

    @MockBean
    protected FeignDiscoveryFunction feignDiscoveryFunction;

    @MockBean
    protected CacheManager cacheManager;

    @MockBean
    protected JavaMailSender javaMailSender;

    @MockBean
    protected MongoProperties mongoProperties;

    @MockBean
    protected MongoConnector mongoConnector;

    @MockBean
    protected CouchConnector couchConnector;

    @MockBean
    protected ElasticConnector elasticConnector;

    @MockBean
    private FeignDiscovery resourceDiscovery;

    @MockBean
    private SchematronRuntimeService schematronRuntimeService;

    @MockBean
    private MongoClient mongoClient;

}
