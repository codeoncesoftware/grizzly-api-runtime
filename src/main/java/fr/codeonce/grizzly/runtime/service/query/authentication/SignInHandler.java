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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.client.MongoClient;
import fr.codeonce.grizzly.common.runtime.RuntimeQueryRequest;
import fr.codeonce.grizzly.common.runtime.SecurityApiConfig;
import fr.codeonce.grizzly.runtime.service.cache.SqlConnector;
import fr.codeonce.grizzly.runtime.service.feign.FeignDiscovery;
import fr.codeonce.grizzly.runtime.service.query.handlers.MongoHandler;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bson.Document;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;

@Service
public class SignInHandler {
    private static final Logger log = LoggerFactory.getLogger(SignInHandler.class);

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
    private FeignDiscovery feignDiscovery;

    @Autowired
    private MongoHandler queryHandler;

    @Autowired
    private SqlConnector sqlConnector;

    private final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @SuppressWarnings("unchecked")
    public Object createToken(SecurityApiConfig securityApiConfig, String username, List<String> roles, boolean defaultIdP) throws InvalidKeySpecException, NoSuchAlgorithmException {
        long now = (new Date()).getTime();
        Date validity;
        validity = new Date(now + (securityApiConfig.getTokenExpiration() * 1000));
        String token = "";
        if (defaultIdP) {
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(
                    Base64.getDecoder().decode(securityApiConfig.getPrivateKey().getBytes()));
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
            token = Jwts.builder().setSubject(username).setHeader((Map<String, Object>) Jwts.header().setType("JWT"))
                    .claim("auth", roles).signWith(privateKey).setExpiration(validity).compact();
        } else {
            Key key = setKey(securityApiConfig.getSecretKey());
            token = Jwts.builder().setSubject(username).setHeader((Map<String, Object>) Jwts.header().setType("JWT"))
                    .claim("auth", roles).claim("iss", securityApiConfig.getClientId())
                    .signWith(key, SignatureAlgorithm.HS256).setExpiration(validity).compact();
        }

        return new Document("token", token);

    }

    private Key setKey(String secretKey) {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    @SuppressWarnings("unchecked")
    public Object handleSignIn(RuntimeQueryRequest resource, String databaseName, MongoClient mongoClient,
                               HttpServletRequest req, HttpServletResponse res, String containerId, String parsedBody) throws Exception {

        resource.setQuery(parsedBody);
        espaceSignInInjection(resource.getQuery());

        resource.setHttpMethod("GET");
        Object result = queryHandler.handleFindQuery(resource, databaseName, mongoClient, null, req, res);
        // verify they match
        if (result != null && result.toString().equals("[]")) {
            res.setStatus(401);
            return new Document("message", "Wrong Credentials");
        } else {
            Document userDocument = (Document) result;
            String username = userDocument.get("username").toString();
            List<String> roles = (List<String>) userDocument.get("roles");
            boolean enabled = (boolean) userDocument.get("enabled");
            if (!enabled) {
                res.setStatus(201);
                return new Document("message", "Your account will be activated soon by the admin");
            }
            return createToken(feignDiscovery.getSecurity(containerId), username, roles, resource.isDefaultIdP());
        }
    }

    /**
     * Parse the query and check if it is malformed or contains a MONGO operator
     * that starts with '${'
     *
     * @param query that is going to be executed
     * @throws IOException, IllegalArgumentException
     */
    private void espaceSignInInjection(String query) {
        try {
            mapper.enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
            JsonNode node = this.mapper.readTree(query);
            if (node.get("password") != null && node.get("password").asText().indexOf("{$") == 0) {
                throw new IllegalArgumentException();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException();
        }
    }

    @SuppressWarnings("unchecked")
    public Object handleSQLSignIn(RuntimeQueryRequest queryRequest, String parsedBody, HttpServletRequest req,
                                  HttpServletResponse res, String containerId) throws Exception {

        // get JDBC connection
        JdbcTemplate jdbcTemplate = sqlConnector.prepareDatasourceClient(queryRequest.getDbsourceId());

        JSONParser parser = new JSONParser();
        JSONObject json = (JSONObject) parser.parse(parsedBody);
        queryRequest.setQuery(parsedBody);
        // espaceSignInInjection(queryRequest.getQuery());

        queryRequest.setHttpMethod("GET");
        String query = "Select * from authentication_user where username ='" + json.get("username").toString() + "' and password = '" + json.get("password").toString() + "';";
        List<Object> result = jdbcTemplate.query(query, new GenericRowMapper());
        // verify they match
        if (result.size() == 0) {
            res.setStatus(401);
            return new Document("message", "Wrong Credentials");
        } else {
            ObjectMapper mapper = new ObjectMapper();
            String rese = mapper.writeValueAsString(result.get(0));
            ObjectNode node = mapper.readValue(rese, ObjectNode.class);
            String userId = node.get("_id").asText();

            String username = node.get("username").asText();
            boolean enabled = node.get("enabled").asBoolean();
            if (!enabled) {
                res.setStatus(201);
                return new Document("message", "Your account will be activated soon by the admin");
            }

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

            return createToken(feignDiscovery.getSecurity(containerId), username, roles, queryRequest.isDefaultIdP());
        }
    }

    public Object handleSQLMe(RuntimeQueryRequest queryRequest, HttpServletRequest req, HttpServletResponse res) throws Exception {

        String meQuery = "select * from authentication_user where username='" + queryRequest.getUsername() + "';";
        // get JDBC connection
        JdbcTemplate jdbcTemplate = sqlConnector.prepareDatasourceClient(queryRequest.getDbsourceId());
        List<Object> result = jdbcTemplate.query(meQuery, new GenericRowMapper());
        ObjectMapper mapper = new ObjectMapper();
        String rese = mapper.writeValueAsString(result.get(0));
        ObjectNode node = mapper.readValue(rese, ObjectNode.class);
        String userId = node.get("_id").asText();


        String queryRoles = "Select role_name from authentication_user_roles where user_id =" + userId + ";";
        List<Object> rolesResult = jdbcTemplate.query(queryRoles, new GenericRowMapper());
        List<String> roles = new ArrayList<String>();
        rolesResult.forEach(eL -> {

            try {
                String roleValue = mapper.writeValueAsString(eL);
                ObjectNode roleNode = mapper.readValue(roleValue, ObjectNode.class);
                roles.add(roleNode.get("role_name").asText());

            } catch (JsonProcessingException e) {
                log.error("role parsing error !");
            }
        });
        node.putPOJO("roles", roles);
        return node.toPrettyString();
    }

}
