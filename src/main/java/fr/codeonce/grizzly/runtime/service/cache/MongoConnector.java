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
package fr.codeonce.grizzly.runtime.service.cache;

import com.mongodb.*;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import fr.codeonce.grizzly.runtime.service.feign.DBSource;
import fr.codeonce.grizzly.runtime.service.feign.FeignDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Service(value = "mongoConnector")
public class MongoConnector implements ICache<MongoClient> {

    private static final String FREE = "FREE";

    private static final String CLOUD = "CLOUD";

    private static final Logger log = LoggerFactory.getLogger(MongoConnector.class);

    @Autowired
    @Qualifier("runtimeCryptoHandler")
    private CryptoHelper cryptoHelper;

    @Autowired
    private FeignDiscovery feignDiscovery;

    @Autowired
    @Qualifier("freeMongoClient")
    private MongoClient atlasMongoClient;

    @Cacheable(value = "runtimeMongoClients", key = "#dbsourceId")
    public MongoClient getMongoClient(String dbsourceId) {
        return this.prepareMongoClient(feignDiscovery.getDBSource(dbsourceId));
    }

    public MongoClient prepareMongoClient(DBSource dbsource) {
        cryptoHelper.decrypt(dbsource);
        try {
            dbsource.setUri(dbsource.getUri());
        } catch (Exception e) {
            log.warn("{}", e.getMessage());
        }
        if (dbsource.getConnectionMode().equalsIgnoreCase(FREE)) {
            return getAtlasMongoClient();
        }
        // IF AN URI IS PRESENT
        if (dbsource.getConnectionMode().equalsIgnoreCase(CLOUD) && !dbsource.getUri().isBlank()) {
            ConnectionString uri = new ConnectionString(dbsource.getUri());
            MongoClientSettings settings = MongoClientSettings.builder().applyToClusterSettings(sett -> {
                sett.serverSelectionTimeout(5000, TimeUnit.MILLISECONDS);

            }).applyToSocketSettings(sett -> {
                sett.connectTimeout(60000, TimeUnit.MILLISECONDS);
            }).applyConnectionString(uri).build();
            return MongoClients.create(settings);

        } else {
            // SERVER INFO
            try {
                ServerAddress serverAddress;
                serverAddress = new ServerAddress(dbsource.getHost(), dbsource.getPort());
                // REMOTE SERVER
                if (dbsource.isSecured()) {
                    // CREDENTIAL
                    MongoCredential credential = MongoCredential.createCredential(dbsource.getUsername(),
                            dbsource.getAuthenticationDatabase(), dbsource.getPassword());

                    MongoClientSettings settings = MongoClientSettings.builder().applyToClusterSettings(sett -> {
                        sett.hosts(Collections.singletonList(serverAddress));
                        sett.serverSelectionTimeout(5000, TimeUnit.MILLISECONDS);
                    }).applyToSocketSettings(sett -> {
                        sett.connectTimeout(60000, TimeUnit.MILLISECONDS);
                    }).credential(credential).build();

                    // CREATE MONGO CLIENT
                    return MongoClients.create(settings);
                } else {

                    MongoClientSettings settings = MongoClientSettings.builder().applyToClusterSettings(sett -> {
                        sett.hosts(Collections.singletonList(serverAddress));
                        sett.serverSelectionTimeout(5000, TimeUnit.MILLISECONDS);
                    }).applyToSocketSettings(sett -> {
                        sett.connectTimeout(60000, TimeUnit.MILLISECONDS);
                    }).build();
                    return MongoClients.create(settings);
                }
            } catch (MongoSocketException e) {
                return null;
            }
        }
    }

    @Cacheable(value = "gridFsTemplates")
    public GridFsTemplate getGridFs(MongoClient mongoClient, String databaseName) {
        MongoTemplate mongoTemplate = new MongoTemplate(mongoClient, databaseName);
        return new GridFsTemplate(mongoTemplate.getMongoDatabaseFactory(), mongoTemplate.getConverter());
    }

    public MongoClient getAtlasMongoClient() {
        return atlasMongoClient;
    }

    @Override
    @CachePut(value = "runtimeMongoClients", key = "#dbsourceId")
    public MongoClient updateCache(String dbsourceId) {
        return this.prepareMongoClient(feignDiscovery.getDBSource(dbsourceId));
    }
}
