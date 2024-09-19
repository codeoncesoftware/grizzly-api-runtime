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
package fr.codeonce.grizzly.runtime.rest;

import fr.codeonce.grizzly.common.runtime.Provider;
import fr.codeonce.grizzly.runtime.service.cache.CouchConnector;
import fr.codeonce.grizzly.runtime.service.cache.ElasticConnector;
import fr.codeonce.grizzly.runtime.service.cache.MarklogicConnector;
import fr.codeonce.grizzly.runtime.service.cache.MongoConnector;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CoreRequestController {

    @Autowired
    private MongoConnector mongoConnector;

    @Autowired
    private ElasticConnector elasticConnector;

    @Autowired
    private CouchConnector couchConnector;

    @Autowired
    private MarklogicConnector marklogicConnector;

    @PutMapping("/runtime/cache/update")
    public void updateCache(@RequestBody Document metaData) {
        Provider provider = Provider.valueOf(metaData.getString("provider"));
        String dbsourceId = metaData.getString("dbsourceId");

        switch (provider) {
            case MONGO:
                mongoConnector.updateCache(dbsourceId);
                break;

            case ELASTICSEARCH:
                elasticConnector.updateCache(dbsourceId);
                break;

            case COUCHDB:
                couchConnector.updateCache(dbsourceId);
                break;

            case MARKLOGIC:
                marklogicConnector.updateCache(dbsourceId);
                break;
        }
    }

}
