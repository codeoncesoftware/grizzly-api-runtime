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

import com.couchbase.client.core.deps.com.fasterxml.jackson.core.JsonProcessingException;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.JsonMappingException;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectMapper;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.node.NullNode;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.node.ObjectNode;
import fr.codeonce.grizzly.common.runtime.Provider;
import fr.codeonce.grizzly.common.runtime.Response;
import fr.codeonce.grizzly.common.runtime.RuntimeQueryRequest;
import fr.codeonce.grizzly.runtime.service.cache.SqlConnector;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.InvalidResultSetAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.Arrays;
import java.util.List;

@Service
public class SqlHandler {

    private static final String QUERY = "query";

    @Autowired
    private QueryUtils queryUtils;

    @Autowired
    private SqlConnector sqlConnector;

    private static final Logger log = LoggerFactory.getLogger(SqlHandler.class);

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

    public Object handleFindQuery(RuntimeQueryRequest queryRequest, Object customQuery, HttpServletRequest req,
                                  HttpServletResponse res) throws Exception {

        if (customQuery instanceof Response) {
            return customQuery;
        }
        JdbcTemplate jdbcTemplate = sqlConnector.prepareDatasourceClient(queryRequest.getDbsourceId());
        // prepare select query + bind query variables
        String query = queryUtils.parseQuery(queryRequest, queryRequest.getQuery(), null, null, req, null).get(QUERY);
        try {
            if (!queryRequest.isMany()) {
                List<Object> result = jdbcTemplate.query(query, new GenericRowMapper());
                if (result.size() == 0) {
                    return jdbcTemplate.query(query, new GenericRowMapper());
                } else
                    return result.get(0);
            } else {
                if (!queryRequest.isPageable()) {
                    return jdbcTemplate.query(query, new GenericRowMapper());

                } else {
                    // Pagination
                    if (req.getParameter("pageNumber") == null && req.getParameter("pageSize") == null) {
                        throw new Exception("You need to provide a pageNumber and pageSize");
                    }
                    if (req.getParameter("pageSize") == null) {
                        throw new Exception("You need to provide a pageSize");
                    }
                    if (req.getParameter("pageNumber") == null) {
                        throw new Exception("You need to provide a pageNumber");
                    }

                    int pageNumber = Integer.parseInt(req.getParameter("pageNumber"));
                    int pageSize = Integer.parseInt(req.getParameter("pageSize"));

                    String pageableQuery = setPagination(queryRequest, query, pageNumber, pageSize);
                    log.info("paginatedQuery {}", pageableQuery);
                    return jdbcTemplate.query(pageableQuery, new GenericRowMapper());

                }
            }
        } catch (BadSqlGrammarException e) {
            throw new Exception("Invalid Qeuery To Execute");
        } catch (InvalidResultSetAccessException e) {
            throw new RuntimeException(e);
        } catch (DataAccessException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new Exception(e);
        }
    }

    private String setPagination(RuntimeQueryRequest queryRequest, String query, int pageNumber, int pageSize)
            throws Exception {
        String pageQuery = "";
        if (!queryRequest.getProvider().name().equalsIgnoreCase("sqlserver")) {
            pageQuery = " LIMIT " + pageSize + " OFFSET " + pageSize * pageNumber;
        } else {
            JdbcTemplate jdbcTemplate = sqlConnector.prepareDatasourceClient(queryRequest.getDbsourceId());
            Connection conn = jdbcTemplate.getDataSource().getConnection();
            String collectioName = getCollectionName(query);
            String schema = collectioName.split("\\.")[0];
            String tableName = collectioName.split("\\.")[1];
            ResultSet pkColumns = conn.getMetaData().getPrimaryKeys(null, schema, tableName);
            String primarykey = "";
            while (pkColumns.next()) {
                primarykey = pkColumns.getString("COLUMN_NAME");
            }
            pageQuery = " ORDER BY " + primarykey + " OFFSET " + pageSize * pageNumber + " ROWS  FETCH NEXT " + pageSize
                    + " ROWS ONLY";
        }
        return query.replaceAll(";", "").concat(pageQuery);
    }

    private String getCollectionName(String query) {
        query = query.substring(query.indexOf("from") + "from".length()).trim();
        String collectionName = query.substring(0, query.trim().indexOf(" "));
        return collectionName;
    }

    public Object handleInsertQuery(RuntimeQueryRequest queryRequest, String parsedBody, Object firstResult,
                                    HttpServletRequest req, HttpServletResponse res) throws Exception {
        // Handle function
        if (queryRequest.getInFunctions() != null && !queryRequest.getInFunctions().isEmpty()) {
            if (firstResult instanceof Response) {// return client exception
                return firstResult;
            } else if (!(firstResult instanceof String)) {
                throw new Exception("Illegal Object To Insert !");
            } else {
                parsedBody = (String) firstResult;
            }

        }
        // get JDBC connection
        JdbcTemplate jdbcTemplate = sqlConnector.prepareDatasourceClient(queryRequest.getDbsourceId());

        JSONParser parser = new JSONParser();
        JSONObject json = (JSONObject) parser.parse(parsedBody);

        // Prepare insert query
        String insertQuery = prepareInsertQuery(json, queryRequest);

        // Get primary key and its value
        Connection conn = jdbcTemplate.getDataSource().getConnection();
        ResultSet pkColumns = null;
        if (queryRequest.getProvider().equals(Provider.SQLSERVER)) {
            String schema = queryRequest.getCollectionName().split("\\.")[0];
            String tableName = queryRequest.getCollectionName().split("\\.")[1];
            pkColumns = conn.getMetaData().getPrimaryKeys(null, schema, tableName);

        } else {
            pkColumns = conn.getMetaData().getPrimaryKeys(null, null, queryRequest.getCollectionName());
        }
        String primarykey = "";
        while (pkColumns.next()) {
            primarykey = pkColumns.getString("COLUMN_NAME");
        }

        // Execute query
        Statement stmt = conn.createStatement();

        stmt.executeUpdate(insertQuery, Statement.RETURN_GENERATED_KEYS);

        ResultSet resultSet = stmt.getGeneratedKeys();
        int id = 0;
        while (resultSet.next()) {
            System.out.println(resultSet);
            id = Integer.valueOf(resultSet.getString(1));
        }
        if (id != 0) {
            StringBuilder returnedQuery = new StringBuilder();
            returnedQuery.append("select * from ").append(queryRequest.getCollectionName()).append(" where ")
                    .append(primarykey).append("=").append(id);
            return jdbcTemplate.query(returnedQuery.toString(), new GenericRowMapper()).get(0);
        }
        return json;
    }

    public Object handleUpdateQuery(RuntimeQueryRequest queryRequest, String parsedBody, Object firstResult,
                                    HttpServletRequest req, HttpServletResponse res) throws Exception {

        // Handle functions
        if (queryRequest.getInFunctions() != null && !queryRequest.getInFunctions().isEmpty()) {
            if (firstResult instanceof Response) {
                return firstResult;
            }
            if (!(firstResult instanceof String)) {
                throw new Exception("Invalid Object To Insert!");
            } else {
                parsedBody = (String) firstResult;
            }
        }

        // Handle sql query
        JdbcTemplate jdbcTemplate = sqlConnector.prepareDatasourceClient(queryRequest.getDbsourceId());
        // Prepare update query
        String updateQuery = queryUtils.parseQuery(queryRequest, queryRequest.getQuery(), null, null, req, parsedBody)
                .get(QUERY);
        int result = jdbcTemplate.update(updateQuery);
        if (result == 0) {
            throw new Exception("not found");
        }

        // Prepare findQuery
        String selectQuery = prepareFindByQuery(updateQuery, queryRequest);
        return jdbcTemplate.query(selectQuery, new GenericRowMapper()).get(0);
    }

    public Object handleSQLUpdateUserQuery(RuntimeQueryRequest queryRequest, String parsedBody, Object firstResult,
                                           HttpServletRequest req, HttpServletResponse res) throws Exception {

        // Handle functions
        if (queryRequest.getInFunctions() != null && !queryRequest.getInFunctions().isEmpty()) {
            if (firstResult instanceof Response) {
                return firstResult;
            }
            if (!(firstResult instanceof String)) {
                throw new Exception("Invalid Object To Insert!");
            } else {
                parsedBody = (String) firstResult;
            }
        }

        // Handle sql query
        JdbcTemplate jdbcTemplate = sqlConnector.prepareDatasourceClient(queryRequest.getDbsourceId());
        // Prepare update query
        String updateQuery = queryUtils.parseQuery(queryRequest, queryRequest.getQuery(), null, null, req, parsedBody)
                .get(QUERY);
        String replacementString = updateQuery.substring(updateQuery.indexOf("username="), updateQuery.indexOf(","));
        JSONParser parser = new JSONParser();
        JSONObject json = (JSONObject) parser.parse(parsedBody);
        updateQuery = updateQuery.replace(replacementString, "username='" + json.get("username") + "'");
        int result = 0;
        try {
            result = jdbcTemplate.update(updateQuery);

        } catch (Exception e) {
            if (e instanceof DuplicateKeyException) {
                throw new SQLIntegrityConstraintViolationException(
                        "username " + json.get("username") + " already exists !");
            }
        }
        if (result == 0) {
            throw new Exception("user not found");
        }

        // Prepare findQuery
        String selectQuery = prepareFindByQuery(updateQuery, queryRequest);
        return jdbcTemplate.query(selectQuery, new GenericRowMapper()).get(0);
    }

    private String jsonParser(JSONObject json, String params) throws ParseException {
        JSONParser parser = new JSONParser();
        List<String> keys = Arrays.asList(params.split("\\."));
        for (int i = 0; i < keys.size() - 1; i++) {
            json = (JSONObject) parser.parse(json.get(keys.get(i)).toString());
        }
        if (StringUtils.isAlpha(json.get(keys.get(keys.size() - 1)).toString()))
            return "'" + json.get(keys.get(keys.size() - 1)).toString() + "'";
        else
            return json.get(keys.get(keys.size() - 1)).toString();
    }

    public void handleDeleteQuery(RuntimeQueryRequest queryRequest, HttpServletRequest req) throws Exception {
        JdbcTemplate jdbcTemplate = sqlConnector.prepareDatasourceClient(queryRequest.getDbsourceId());
        String query = queryUtils.parseQuery(queryRequest, queryRequest.getQuery(), null, null, req, null).get(QUERY);
        int result = jdbcTemplate.update(query);
        if (result == 0) {
            throw new Exception("not found");
        }
    }

    public String prepareInsertQuery(JSONObject json, RuntimeQueryRequest queryRequest) throws Exception {
        if (json.toString().equals("{}")) {
            throw new Exception("You need to provide a valid JSON ! ");
        }
        String insertQuery = "";
        // prepare insert query and bind the values
        if (queryRequest.getProvider().name().toLowerCase().equals("sqlserver")
                || queryRequest.getProvider().name().toLowerCase().equals("postgresql")) {
            insertQuery = "insert into " + queryRequest.getCollectionName() + "(";
        } else {
            insertQuery = "insert into `" + queryRequest.getCollectionName() + "`(";
        }
        String fields = "";
        String values = "";
        for (var key : json.keySet()) {
            if (json.get(key) == null) {
                throw new Exception(key + " is null !!");
            }
            if (queryRequest.getProvider().name().toLowerCase().equals("sqlserver")
                    || queryRequest.getProvider().name().toLowerCase().equals("postgresql")) {
                fields += key;
            } else {
                fields += "`" + key + "`";
            }
            fields += ",";
            // transform boolean values with (0-1) & surround string values with double
            // quotes
            if (StringUtils.isNumeric(json.get(key).toString())) {
                values += json.get(key) + ",";
            } else if (json.get(key).toString().equals("true")) {
                values += 1 + ",";
            } else if (json.get(key).toString().equals("false")) {
                values += 0 + ",";
            } else {
                values += "'" + json.get(key) + "',";
            }
        }
        if (fields.length() > 0) {
            fields = fields.substring(0, fields.length() - 1);
        }
        fields += ") values (";

        if (values.length() > 0) {
            values = values.substring(0, values.length() - 1);
        }
        values += ");";

        insertQuery += fields + values;
        return insertQuery;
    }

    private String prepareFindByQuery(String query, RuntimeQueryRequest runtimeRequest) {
        StringBuilder returnedQuery = new StringBuilder();
        String whereClause = query.substring(query.toLowerCase().indexOf("where") + "where".length() + 1);
        returnedQuery.append("select * from ").append(runtimeRequest.getCollectionName()).append(" where ")
                .append(whereClause);
        return returnedQuery.toString();
    }

}
