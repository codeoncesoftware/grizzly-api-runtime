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

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.env.ClusterEnvironment;
import fr.codeonce.grizzly.runtime.service.feign.DBSource;
import fr.codeonce.grizzly.runtime.service.feign.FeignDiscovery;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service(value = "couchConnector")
public class CouchConnector implements ICache<Bucket> {

    @Autowired
    @Qualifier("runtimeCryptoHandler")
    private CryptoHelper encryption;

    @Autowired
    private FeignDiscovery feignDiscovery;

    private final ClusterEnvironment environment = ClusterEnvironment.create();

    private static final Logger log = LoggerFactory.getLogger(CouchConnector.class);

    @Cacheable(value = "runtimeBuckets", key = "#dbsourceId")
    public Bucket connectToBucket(String dbsourceId) {

        return buildBucket(dbsourceId);

    }


    private Bucket buildBucket(String dbsourceId) {
        DBSource db = feignDiscovery.getDBSource(dbsourceId);
        try {
            db.setHost(encryption.decrypt(db.getHost()));
            Cluster cluster = getCluster(db.getHost());
            return getBucket(cluster, db.getBucketName(), String.valueOf(db.getPassword()));
        } catch (Exception e) {
            log.warn("Can't get DBSource from Core with feign : {}", e.getMessage());
            throw new IllegalArgumentException("Arguments for CouchBaseDB connection do not match ! ");
        }
    }


    public Cluster getCluster(String node) {
        try {
            Cluster cluster = Cluster.connect(node, null);
            return cluster;
        } catch (Exception e) {
            throw new IllegalArgumentException("Bucket informations are invalid");
        }
    }


    private Bucket getBucket(Cluster cluster, String name, String password) {

        if (!StringUtils.isEmpty(password)) {
            Bucket bucket = cluster.bucket(name);
            return bucket;
        } else {
            return cluster.bucket(name);
        }

    }


    @Override
    @CachePut(value = "runtimeBuckets", key = "#dbsourceId")
    public Bucket updateCache(String dbsourceId) {
        return buildBucket(dbsourceId);
    }

}
