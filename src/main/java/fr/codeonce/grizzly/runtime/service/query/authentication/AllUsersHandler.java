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
import com.mongodb.client.MongoClient;
import fr.codeonce.grizzly.common.runtime.RuntimeQueryRequest;
import fr.codeonce.grizzly.runtime.service.cache.SqlConnector;
import fr.codeonce.grizzly.runtime.service.query.handlers.QueryUtils;
import fr.codeonce.grizzly.runtime.util.DocumentHexIdHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.bson.Document;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AllUsersHandler {

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
    private QueryUtils queryUtils;

    private static final Logger log = LoggerFactory.getLogger(AllUsersHandler.class);


    public List<Document> handleAllUsers(RuntimeQueryRequest queryRequest, String databaseName, MongoClient mClient,
                                         HttpServletRequest req) throws Exception {

        // The Query String as a MongoQuery Object
        Map<String, String> finalQuery = queryUtils.parseQuery(queryRequest, queryRequest.getQuery(), mClient,
                databaseName, req, null);
        // List for the Result Elements
        List<Document> result = new ArrayList<>();

        if (mClient != null) {
            MongoTemplate mongoTemplate = new MongoTemplate(mClient, databaseName);
            // Get DB Query
            BasicQuery query = new BasicQuery(finalQuery.get("Query"));
            log.info("query {}", query);
            mongoTemplate.executeQuery(query, queryRequest.getCollectionName(),
                    document -> result.add(DocumentHexIdHandler.transformMongoHexID(document)));
        }

        result.forEach(element -> element.remove("password"));
        queryRequest.setReturnType("application/json");

        return result;
    }

    public Object handleSqlAllUsers(RuntimeQueryRequest queryRequest, String databaseName,
                                    HttpServletRequest req) throws Exception {
        List<Object> finalResult = new ArrayList<Object>();
        String allUsersQuery = "SELECT _id, firstname, lastname, username, email, phone, enabled FROM authentication_user;";
        // get JDBC connection
        JdbcTemplate jdbcTemplate = sqlConnector.prepareDatasourceClient(queryRequest.getDbsourceId());
        List<Object> result = jdbcTemplate.query(allUsersQuery, new GenericRowMapper());
        ObjectMapper mapper = new ObjectMapper();
        result.forEach(user -> {
            try {
                String rese = mapper.writeValueAsString(user);
                ObjectNode node = mapper.readValue(rese, ObjectNode.class);
                String userId = node.get("_id").asText();
                List<String> roles = this.getoles(jdbcTemplate, mapper, userId);
                node.putPOJO("roles", roles);
                JSONParser parser = new JSONParser();
                JSONObject json = (JSONObject) parser.parse(node.toPrettyString());
                finalResult.add(json);
            } catch (JsonProcessingException | ParseException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        });

        queryRequest.setReturnType("application/json");

        return finalResult;
    }

    public List<String> getoles(JdbcTemplate jdbcTemplate, ObjectMapper mapper, String userId) {
        String queryRoles = "Select role_name from authentication_user_roles where user_id =" + userId + ";";
        List<Object> rolesResult = jdbcTemplate.query(queryRoles, new GenericRowMapper());
        List<String> roles = new ArrayList<String>();
        rolesResult.forEach(eL -> {
            String roleValue;
            try {
                roleValue = mapper.writeValueAsString(eL);
                ObjectNode roleNode = mapper.readValue(roleValue, ObjectNode.class);
                roles.add(roleNode.get("role_name").asText());

            } catch (JsonProcessingException e) {
                log.error("role parsing error !");
            }
        });
        return roles;
    }
}
