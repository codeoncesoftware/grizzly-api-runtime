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
package fr.codeonce.grizzly.runtime.service.resolver;

import com.mongodb.client.MongoClient;
import com.mongodb.client.gridfs.model.GridFSFile;
import fr.codeonce.grizzly.runtime.service.ContainerContextHolder;
import fr.codeonce.grizzly.runtime.service.cache.MongoConnector;
import fr.codeonce.grizzly.runtime.service.feign.FeignDiscovery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@Service
@ConditionalOnProperty(value = "docker", havingValue = "false")
public class MongoResolverService implements ICloudResolverService {

    @Autowired
    private FeignDiscovery feignDiscovery;

    @Autowired
    private MongoConnector mongoConnector;

    @Override
    public InputStream getInputStream(String path) throws IOException {
        String containerId = ContainerContextHolder.getContext();

        try {
            Map<String, String> map = this.feignDiscovery.getContainerInfos(containerId);

            MongoClient mongoClient = mongoConnector.getMongoClient(map.get("dbsourceId"));
            GridFsTemplate gridFsTemplate = mongoConnector.getGridFs(mongoClient, map.get("dbname"));
            GridFSFile file = gridFsTemplate.findOne(Query
                    .query((Criteria.where("metadata.fileUri").is(path).and("metadata.containerId").is(containerId))));
            GridFsResource resource = gridFsTemplate.getResource(file);
            return resource.getInputStream();

        } catch (NullPointerException ex) {
            throw new IllegalArgumentException(
                    "this file path : %s is not valid under this container %s ".formatted(path, containerId));

        }
    }
}
