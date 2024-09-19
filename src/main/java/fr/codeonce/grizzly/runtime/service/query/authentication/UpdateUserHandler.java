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
package fr.codeonce.grizzly.runtime.service.query.authentication;

import com.couchbase.client.core.deps.com.fasterxml.jackson.core.JsonProcessingException;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.JsonMappingException;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectMapper;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.node.NullNode;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.client.MongoClient;
import com.mongodb.client.result.UpdateResult;
import fr.codeonce.grizzly.common.runtime.RuntimeQueryRequest;
import fr.codeonce.grizzly.runtime.service.cache.SqlConnector;
import fr.codeonce.grizzly.runtime.service.query.handlers.MongoHandler;
import fr.codeonce.grizzly.runtime.service.query.handlers.QueryUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@Service
public class UpdateUserHandler {

    private static final Logger log = LoggerFactory.getLogger(UpdateUserHandler.class);

    class GenericRowMapper implements RowMapper<Object> {
        @Override
        public Object mapRow(ResultSet rs, int rowNum) throws SQLException {
            ObjectNode node = new ObjectMapper().createObjectNode();
            ResultSetMetaData metadata = rs.getMetaData();
            int numColumns = metadata.getColumnCount();
            for (int i = 1; i <= numColumns; ++i) {
                String column_name = metadata.getColumnName(i);
                if (rs.getObject(column_name) != null) {
                    node.put(column_name, rs.getObject(column_name).toString());
                } else
                    node.put(column_name, NullNode.getInstance());
            }
            try {
                return new ObjectMapper().readValue(node.toString(), Object.class);
            } catch (JsonMappingException e) {
                e.printStackTrace();
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    @Autowired
    private SqlConnector sqlConnector;

    @Autowired
    private MongoHandler queryHandler;

    @Autowired
    private QueryUtils queryUtils;

    public Object handleUpdateUser(RuntimeQueryRequest queryRequest, String databaseName, MongoClient mClient,
                                   HttpServletRequest req, String containerId, String parsedBody) throws Exception {

        queryRequest.getParameters().get(0).setValue(queryRequest.getUsername());

        Document userDocument = Document.parse(parsedBody);

        UpdateResult updateResult = null;
        Object queryBodyParsed = null;

        if (mClient != null) {
            MongoTemplate mongoTemplate = new MongoTemplate(mClient, databaseName);

            Map<String, String> finalQuery = queryUtils.parseQuery(queryRequest, queryRequest.getQuery(), mClient,
                    databaseName, req, null);
            BasicQuery basicQuery = new BasicQuery(finalQuery.get("query"));
            // Apply Action on One Or Many
            if (!queryRequest.isMany()) {
                basicQuery.limit(1);
            }

            Update update = new Update();
            userDocument.forEach(update::set);
            Object finalResult1 = Document.parse(parsedBody);
            if (finalResult1 != null) {
                queryBodyParsed = (Document) finalResult1;
            } else {

                queryBodyParsed = Document.parse(parsedBody);
            }
            com.fasterxml.jackson.databind.ObjectMapper jsonWriter = new com.fasterxml.jackson.databind.ObjectMapper();
            JsonNode jsonBody = jsonWriter.readTree(parsedBody);
            Query query = new Query();
            if (jsonBody.get("_id") != null) {
                query.addCriteria(new Criteria("_id").is(jsonBody.get("_id").asText()));
            }
            updateResult = mongoTemplate.updateFirst(query, update, queryRequest.getCollectionName());
        }
        return queryBodyParsed;
    }

    public Object activate(RuntimeQueryRequest queryRequest, String databaseName, MongoClient mClient,
                           HttpServletRequest req, HttpServletResponse res) throws Exception {

        String username = "Username";

        Object findResult = queryHandler.handleFindQuery(queryRequest, databaseName, mClient, null, req, res);
        if (findResult == null || findResult.toString().equals("[]")) {
            res.setStatus(404);
            return new Document("message", "Username does not exist");
        }
        Document user = (Document) findResult;
        user.put("enabled", true);

        if (mClient != null) {
            MongoTemplate mongoTemplate = new MongoTemplate(mClient, databaseName);

            Map<String, String> finalQuery = queryUtils.parseQuery(queryRequest, queryRequest.getQuery(), mClient,
                    databaseName, req, null);
            BasicQuery basicQuery = new BasicQuery(finalQuery.get("query"));
            // Apply Action on One Or Many
            if (!queryRequest.isMany()) {
                basicQuery.limit(1);
            }

            Update update = new Update();
            user.forEach(update::set);

            username = basicQuery.getQueryObject().getString("username");

            mongoTemplate.updateMulti(basicQuery, update, queryRequest.getCollectionName());

        }
        return new Document("message", "The account for " + username + " has been activated");

    }

    public Object activateUserSQL(RuntimeQueryRequest queryRequest, HttpServletRequest req, HttpServletResponse res)
            throws Exception {

        String getUser = "select * from authentication_user where username='" + req.getParameter("username") + "';";
        // get JDBC connection
        JdbcTemplate jdbcTemplate = sqlConnector.prepareDatasourceClient(queryRequest.getDbsourceId());
        List<Object> result = jdbcTemplate.query(getUser, new GenericRowMapper());
        if (result.size() <= 0) {
            res.setStatus(404);
            return new Document("message", "Username does not exist");
        }
        String enabled = queryRequest.getProvider().name().toLowerCase().equals("postgresql") ? "true" : "1";
        String activateUserQuery = "update authentication_user set enabled=" + enabled + " where username='"
                + req.getParameter("username") + "';";
        jdbcTemplate.update(activateUserQuery);

        return new Document("message", "The account for " + req.getParameter("username") + " has been activated");

    }

}
