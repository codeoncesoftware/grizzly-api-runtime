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
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mongodb.client.MongoClient;
import fr.codeonce.grizzly.common.runtime.RuntimeQueryRequest;
import fr.codeonce.grizzly.runtime.service.cache.SqlConnector;
import fr.codeonce.grizzly.runtime.service.query.handlers.MongoHandler;
import fr.codeonce.grizzly.runtime.service.query.handlers.SqlHandler;
import fr.codeonce.grizzly.runtime.util.DocumentHexIdHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bson.Document;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class SignUpHandler {
    private static final Logger log = LoggerFactory.getLogger(SignUpHandler.class);

    private static final String MESSAGE = "message";

    private static final String USERNAME = "username";

    @Value("${frontUrl}")
    private String url;

    @Autowired
    private MongoHandler queryHandler;

    @Autowired
    private SqlHandler sqlHandler;
    @Autowired
    private SqlConnector sqlConnector;

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

    @SuppressWarnings("unchecked")
    public Object handleSignUp(RuntimeQueryRequest queryRequest, String databaseName, MongoClient mClient,
                               HttpServletRequest req, HttpServletResponse res, String parsedBody) throws Exception {

        String body = parsedBody;
        JSONParser parser = new JSONParser();
        JSONObject bodyJson = (JSONObject) parser.parse(body);
        if (verifyFields(bodyJson) != null) {
            res.setStatus(400);
            return verifyFields(bodyJson);
        }
        String username = (String) bodyJson.get(USERNAME);
        JSONObject query = new JSONObject();
        query.put(USERNAME, username);
        queryRequest.setQuery(query.toString());
        queryRequest.setHttpMethod("GET");
        Object findResult = queryHandler.handleFindQuery(queryRequest, databaseName, mClient, null, req, res);
        // verify unicity
        if (findResult != null && !findResult.toString().equals("[]")) {
            res.setStatus(302);
            return new Document(MESSAGE, "User with current username already exists");
        } else {
            return insertRequest(queryRequest, databaseName, mClient, bodyJson);
        }
    }


    @SuppressWarnings("unchecked")
    private Object insertRequest(RuntimeQueryRequest queryRequest, String databaseName, MongoClient mClient,
                                 JSONObject bodyJson) {
        List<Document> result = new ArrayList<>();
        if (mClient != null) {
            bodyJson.put("roles", Arrays.asList("user"));
            bodyJson.put("enabled", false);
            String body = bodyJson.toString();
            MongoTemplate mongoTemplate = new MongoTemplate(mClient, databaseName);
            // Test if the Given Query have Multiple Objects
            if (body.length() > 1) {
                if (body.substring(0, 1).equals("[")) {
                    JsonArray jsonArray = new JsonParser().parse(body).getAsJsonArray();
                    jsonArray.forEach(element -> {
                        String jsonToInsert = element.toString();
                        Document document = mongoTemplate.insert(Document.parse(jsonToInsert),
                                queryRequest.getCollectionName());
                        result.add(DocumentHexIdHandler.transformMongoHexID(document));
                    });
                } else {
                    Document document = mongoTemplate.insert(Document.parse(body), queryRequest.getCollectionName());
                    result.add(DocumentHexIdHandler.transformMongoHexID(document));
                }
            }

        }

        return result.size() == 1 ? result.get(0) : result;
    }

    Document verifyFields(JSONObject bodyJson) {

        if (bodyJson.get(USERNAME) == null) {
            return new Document(MESSAGE, "The username field is required");
        } else if (bodyJson.get("password") == null) {
            return new Document(MESSAGE, "The password field is required");
        } else if (bodyJson.get("email") == null) {
            return new Document(MESSAGE, "The email field is required");
        } else if (bodyJson.get("firstname") == null) {
            return new Document(MESSAGE, "The firstname field is required");
        } else if (bodyJson.get("lastname") == null) {
            return new Document(MESSAGE, "The lastname field is required");
        } else if (bodyJson.get("phone") == null) {
            return new Document(MESSAGE, "The phone field is required");
        }
        return null;
    }

    public Object handleSqlSignUp(RuntimeQueryRequest queryRequest, String parsedBody, HttpServletRequest req,
                                  HttpServletResponse res) throws Exception {
        // get JDBC connection
        JdbcTemplate jdbcTemplate = sqlConnector.prepareDatasourceClient(queryRequest.getDbsourceId());
        int userId = this.insertUser(queryRequest, parsedBody, req, res);
        int roleId = this.insertRole(queryRequest);
        String insertUserRolesQuery = "INSERT INTO authentication_user_roles (user_id, role_name) VALUES ( "
                + userId + ", 'user');";
        jdbcTemplate.execute(insertUserRolesQuery);
        StringBuilder returnedQuery = new StringBuilder();
        returnedQuery.append("select * from ").append(queryRequest.getCollectionName()).append(" where ").append("_id")
                .append("=").append(userId);
        Object user = jdbcTemplate.query(returnedQuery.toString(), new GenericRowMapper()).get(0);
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(user);
        String[] array = {"user"};
        ObjectNode node = mapper.readValue(json, ObjectNode.class);
        node.putPOJO("roles", array);
        return node.toPrettyString();
    }

    private int insertRole(RuntimeQueryRequest queryRequest) throws Exception {
        String insertRolesQuery = "INSERT INTO authentication_roles (name) VALUES ( 'user');";

        // get JDBC connection
        JdbcTemplate jdbcTemplate = sqlConnector.prepareDatasourceClient(queryRequest.getDbsourceId());

        // Get primary key and its value
        Connection conn = jdbcTemplate.getDataSource().getConnection();

        // Execute query
        Statement stmt = conn.createStatement();
        stmt.executeUpdate(insertRolesQuery, Statement.RETURN_GENERATED_KEYS);

        ResultSet resultSet = stmt.getGeneratedKeys();
        int id = 0;
        while (resultSet.next()) {
            id = Integer.valueOf(resultSet.getString(1));
        }
        return id;
    }

    private int insertUser(RuntimeQueryRequest queryRequest, String parsedBody, HttpServletRequest req,
                           HttpServletResponse res) throws Exception {
        // get JDBC connection
        JdbcTemplate jdbcTemplate = sqlConnector.prepareDatasourceClient(queryRequest.getDbsourceId());

        JSONParser parser = new JSONParser();
        JSONObject json = (JSONObject) parser.parse(parsedBody);

        // Prepare insert query
        String insertUserQuery = sqlHandler.prepareInsertQuery(json, queryRequest);

        // Get primary key and its value
        Connection conn = jdbcTemplate.getDataSource().getConnection();

        // Execute query
        Statement stmt = conn.createStatement();
        try {
            stmt.executeUpdate(insertUserQuery, Statement.RETURN_GENERATED_KEYS);

        } catch (SQLIntegrityConstraintViolationException e) {
            throw new SQLIntegrityConstraintViolationException("username " + json.get("username") + " already exists !");
        }
        ResultSet resultSet = stmt.getGeneratedKeys();
        int id = 0;
        while (resultSet.next()) {
            id = Integer.valueOf(resultSet.getString(1));
        }

        return id;
    }
}
