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
package fr.codeonce.grizzly.runtime.test.query.runtime.xsl;

import fr.codeonce.grizzly.common.runtime.RuntimeRequest;
import fr.codeonce.grizzly.runtime.AbstractRuntimeTest;
import fr.codeonce.grizzly.runtime.service.resolver.CloudURLStreamHandler;
import fr.codeonce.grizzly.runtime.service.xsl.XslRuntimeService;
import org.apache.catalina.webresources.TomcatURLStreamHandlerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.IOException;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.nio.file.Files;
import java.util.Optional;

/*
public class XslServiceTest extends AbstractRuntimeTest {

    @Autowired
    private CloudURLStreamHandler cloudURLStreamHandler;


    @Autowired
    private XslRuntimeService xslService;

    @Before
    public void init() {
        TomcatURLStreamHandlerFactory.getInstance().addUserFactory(new URLStreamHandlerFactory() {
            @Override
            public URLStreamHandler createURLStreamHandler(String protocol) {
                if (CloudURLStreamHandler.CLOUD_PROTOCOL.equals(protocol)) {
                    return cloudURLStreamHandler;
                }
                return null;
            }

        });
    }

    @Test
    public void handleTest() throws IOException {


        File xmlFile = ResourceUtils.getFile("classpath:xml/catalog.xml");
        String xmlContent = new String(
                Files.readAllBytes(xmlFile.toPath()));

        RuntimeRequest<String> request = new RuntimeRequest<String>();
        request.setBody(xmlContent);
        request.setContainerId("46463457");
        request.setParams(null);
        request.setExecutablePath("xsl/catalog.xsl");
        request.setSecondaryFilePaths(null);

        Optional<String> res = xslService.handle(request, "localhost:8090");

        assertNotNull(res.get());


    }
}
*/
