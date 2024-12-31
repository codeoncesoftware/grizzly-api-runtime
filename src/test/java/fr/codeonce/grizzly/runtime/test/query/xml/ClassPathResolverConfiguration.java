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
package fr.codeonce.grizzly.runtime.test.query.xml;

import fr.codeonce.grizzly.runtime.service.resolver.ICloudResolverService;
import org.apache.commons.io.FileUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class ClassPathResolverConfiguration {

    @Bean
    @Primary
    public ICloudResolverService classPathResolverService() {
        return new ICloudResolverService() {

            @Override
            public InputStream getInputStream(String path) throws IOException {
                return FileUtils.openInputStream(new ClassPathResource(path).getFile());
            }
        };
    }

}
