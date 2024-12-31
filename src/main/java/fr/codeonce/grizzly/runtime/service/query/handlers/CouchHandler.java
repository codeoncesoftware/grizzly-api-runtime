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
package fr.codeonce.grizzly.runtime.service.query.handlers;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.kv.GetResult;
import com.couchbase.client.java.kv.MutationResult;
import fr.codeonce.grizzly.common.runtime.RuntimeQueryRequest;
import fr.codeonce.grizzly.runtime.service.cache.CouchConnector;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CouchHandler {

    private static final String QUERY = "query";

    @Autowired
    private QueryUtils queryUtils;

    @Autowired
    private CouchConnector connector;

    @Value("${frontUrl}")
    private String frontUrl;

    public Object handleFindQuery(RuntimeQueryRequest queryRequest, HttpServletRequest req, HttpServletResponse res) throws Exception {
        Bucket bucket = this.connector.connectToBucket(queryRequest.getDbsourceId());
        Cluster cluster = this.connector.getCluster(queryRequest.getHost());
        cluster.queryIndexes().createPrimaryIndex(queryRequest.getBucketName());
        String query = queryUtils.parseQuery(queryRequest, queryRequest.getQuery(), null, null, req, null).get(QUERY);
        return cluster.query(query).rowsAsObject();
//				.stream()
//				.map(row -> row.toMap().get(queryRequest.getBucketName()).collect(Collectors.toList()));
    }

    public Object handleInsertQuery(RuntimeQueryRequest queryRequest, String parsedBody, HttpServletRequest req,
                                    HttpServletResponse res, Object finalResult) {
        Bucket bucket = this.connector.connectToBucket(queryRequest.getDbsourceId());
        UUID uuid = UUID.randomUUID();
        Cluster cluster = this.connector.getCluster(queryRequest.getHost());
        cluster.queryIndexes().createPrimaryIndex(queryRequest.getBucketName());
        JsonObject content = JsonObject.fromJson(parsedBody);
        content.put("id", uuid.toString());
        Collection collection = cluster.bucket(queryRequest.getBucketName())
                .collection(queryRequest.getCollectionName());
        MutationResult upsertResult = collection.upsert("id", JsonObject.create());
        GetResult getResult = collection.get("id");
        return getResult;
    }

    public Object handleUpdateQuery(RuntimeQueryRequest queryRequest, String parsedBody, HttpServletRequest req,
                                    HttpServletResponse res) throws Exception {
        Bucket bucket = this.connector.connectToBucket(queryRequest.getDbsourceId());
        Cluster cluster = this.connector.getCluster(queryRequest.getHost());
        cluster.queryIndexes().createPrimaryIndex(queryRequest.getBucketName());
        String query = queryUtils.parseQuery(queryRequest, queryRequest.getQuery(), null, null, req, null).get(QUERY);
        return cluster.query(query).rowsAsObject();
//				.parallelStream()
//				.map(row -> row.toMap().get(queryRequest.getBucketName()).collect(Collectors.toList()));
    }

    public void handleDeleteQuery(RuntimeQueryRequest queryRequest, HttpServletRequest req) throws Exception {
        Bucket bucket = this.connector.connectToBucket(queryRequest.getDbsourceId());
        Cluster cluster = this.connector.getCluster(queryRequest.getHost());
        cluster.queryIndexes().createPrimaryIndex(queryRequest.getBucketName());
        String query = queryUtils.parseQuery(queryRequest, queryRequest.getQuery(), null, null, req, null).get(QUERY);
        cluster.query(query);

    }

}