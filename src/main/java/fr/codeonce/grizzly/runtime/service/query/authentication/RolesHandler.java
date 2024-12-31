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
import fr.codeonce.grizzly.runtime.service.feign.FeignDiscovery;
import fr.codeonce.grizzly.runtime.service.query.handlers.MongoHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

@Service
public class RolesHandler {
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
    private FeignDiscovery feignDiscovery;

    @Autowired
    private MongoHandler queryHandler;

    public Object handleAllRoles(RuntimeQueryRequest queryRequest, String databaseName, MongoClient mClient,
                                 HttpServletRequest req, HttpServletResponse res, String containerId) {
        return feignDiscovery.getRoles(containerId);
    }

    public Object handleGrantRoles(RuntimeQueryRequest queryRequest, String databaseName, MongoClient mClient,
                                   HttpServletRequest req, HttpServletResponse res, String containerId, String parsedBody) throws Exception {

        queryRequest.setHttpMethod("GET");
        Object findResult = queryHandler.handleFindQuery(queryRequest, databaseName, mClient, null, req, res);
        if (findResult.toString().trim().equals("[]")) {
            res.setStatus(404);
            return new Document("message", "Username does not exist");
        }

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) Document.parse(parsedBody).get("roles");

        List<String> definedRoles = feignDiscovery.getRoles(containerId);
        if (checkRoles(definedRoles, roles)) {
            queryRequest.setHttpMethod("PUT");
            return queryHandler.handleUpdateQuery(queryRequest, databaseName, mClient, req, parsedBody, null);
        } else {
            res.setStatus(404);
            return new Document("message", "This role does not exist. Please enter a valid role.");
        }
    }

    public Object handleSQLGrantRoles(RuntimeQueryRequest queryRequest, String databaseName,
                                      HttpServletRequest req, HttpServletResponse res, String containerId, String parsedBody) throws Exception {

        queryRequest.setHttpMethod("GET");
        String getUser = "select * from authentication_user where username='" + req.getParameter("username") + "';";
        // get JDBC connection
        JdbcTemplate jdbcTemplate = sqlConnector.prepareDatasourceClient(queryRequest.getDbsourceId());
        List<Object> result = jdbcTemplate.query(getUser, new GenericRowMapper());
        if (result.size() <= 0) {
            res.setStatus(404);
            return new Document("message", "Username does not exist");
        }

        ObjectMapper mapper = new ObjectMapper();
        String rese = mapper.writeValueAsString(result.get(0));
        ObjectNode node = mapper.readValue(rese, ObjectNode.class);
        String userId = node.get("_id").asText();

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) Document.parse(parsedBody).get("roles");
        List<String> definedRoles = feignDiscovery.getRoles(containerId);
        String ignore = (queryRequest.getProvider().name().equalsIgnoreCase("postgresql") || queryRequest.getProvider().name().equalsIgnoreCase("sqlserver")) ? "" : " IGNORE ";
        if (checkRoles(definedRoles, roles)) {
            queryRequest.setHttpMethod("PUT");
            roles.forEach(role -> {
                String insertQuery = "INSERT " + ignore + "INTO authentication_roles (name) VALUES ('" + role + "');";

                jdbcTemplate.execute(insertQuery);
                String userRolesQuery = "INSERT  " + ignore + " INTO authentication_user_roles (user_id, role_name) VALUES ('"
                        + userId + "',  '" + role + "');";
                jdbcTemplate.execute(userRolesQuery);

            });
            return new Document("message", "Roles granted with success !");
        } else {
            res.setStatus(404);
            return new Document("message", "This role does not exist. Please enter a valid role.");
        }
    }

    private boolean checkRoles(List<String> definedRoles, List<String> roles) {
        for (String role : roles) {
            if (!definedRoles.contains(role)) {
                return false;
            }
        }
        return true;
    }

}
