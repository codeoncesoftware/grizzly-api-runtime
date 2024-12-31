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
package fr.codeonce.grizzly.runtime.service.thymeleaf;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.codeonce.grizzly.common.runtime.RuntimeRequest;
import fr.codeonce.grizzly.runtime.service.resolver.CloudURLStreamHandler;
import fr.codeonce.grizzly.runtime.service.shared.PostTreatment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ITemplateResolver;
import org.thymeleaf.templateresolver.UrlTemplateResolver;

import java.io.IOException;
import java.util.Map;

@Service
public class ThymeLeafService {

    @Autowired
    private PostTreatment postTreatment;

    public String executeTemplate(RuntimeRequest<?> request, String baseUrl) throws JsonParseException, JsonMappingException, IOException {
        final SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.addTemplateResolver(urlTemplateResolver());

        // Prepare the evaluation context
        final Context ctx = new Context();

        // map objects from JSON body
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> variables = mapper.readValue(request.getBody().toString(),
                new TypeReference<Map<String, Object>>() {
                });

        ctx.setVariables(variables);


        String fileUri = CloudURLStreamHandler.CLOUD_PROTOCOL + "://" + request.getExecutablePath();
        String htmlOutput = templateEngine.process(fileUri, ctx);

        return postTreatment.htmlTreatment(htmlOutput, fileUri, request.getContainerId(), request.getSecondaryFilePaths());

    }

    private ITemplateResolver urlTemplateResolver() {
        final UrlTemplateResolver templateResolver = new UrlTemplateResolver();
        templateResolver.setTemplateMode("HTML");
        templateResolver.setCacheable(true);
        return templateResolver;
    }
}
