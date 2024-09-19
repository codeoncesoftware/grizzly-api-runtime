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
package fr.codeonce.grizzly.runtime.service.freemarker;

import freemarker.cache.URLTemplateLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;

public class CloudURLTemplateLoader extends URLTemplateLoader {


    private static final Logger log = LoggerFactory.getLogger(CloudURLTemplateLoader.class);


    @Override
    protected URL getURL(String name) {
        try {
            return new URL(name);
        } catch (MalformedURLException e) {
            log.debug("malformated exception : {}", e);
        }
        return null;
    }

}
