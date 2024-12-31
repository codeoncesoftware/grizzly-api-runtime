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

import com.mongodb.client.MongoClient;
import fr.codeonce.grizzly.common.runtime.RuntimeQueryRequest;
import fr.codeonce.grizzly.common.runtime.resource.RuntimeResourceParameter;
import fr.codeonce.grizzly.runtime.service.query.SessionParamsHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class QueryUtils {

    private String QUERY = "query";

    private static Pattern p = Pattern.compile("^[a-zA-Z0-9]*$");

    @Autowired
    private SessionParamsHandler sessionParamsHandler;

    private static final Logger log = LoggerFactory.getLogger(QueryUtils.class);

    /**
     * Parse a Query and Replace parameters with their values
     *
     * @param queryRequest
     * @param query
     * @param mClient
     * @param dbName
     * @param req
     * @return final Query to execute
     * @throws Exception
     */
    public Map<String, String> parseQuery(RuntimeQueryRequest queryRequest, String query, MongoClient mClient,
                                          String dbName, HttpServletRequest req, String parsedBody) throws Exception {

        //INIT
        List<String> paths = new ArrayList<String>();
        Map<String, String> values = new HashMap<String, String>();

        Map<String, String> finalQuery = new HashMap<>();
        finalQuery.put(QUERY, query);

        List<RuntimeResourceParameter> endpointParameters = queryRequest.getParameters();

        // For MONGO

        if (!ObjectUtils.isEmpty(mClient)) {
            String updatedQueryWithSessionParams = this.parseSessionParams(queryRequest, query, mClient, dbName);
            finalQuery.put(QUERY, updatedQueryWithSessionParams);
        }

        Map<String, String> pathVariablesMap = new HashMap<>();
        if (queryRequest.getPath().contains("{")) {
            if (req.getHeader("q") != null) {
                finalQuery.put(QUERY, getQuery(req, queryRequest,
                        getPathVariables(queryRequest, req.getServletPath(), pathVariablesMap), paths));
            } else {
                getPathVariables(queryRequest, req.getServletPath(), pathVariablesMap);
            }
        }

        Map<String, List<String>> headersParamsMap = Collections.list(req.getHeaderNames()).stream()
                .collect(Collectors.toMap(Function.identity(), h -> Collections.list(req.getHeaders(h))));
        if (endpointParameters != null && !endpointParameters.isEmpty()) {
            endpointParameters.stream().forEach(param -> {
                try {
                    setQueryParameters(queryRequest, req, finalQuery, pathVariablesMap, headersParamsMap, param, values);
                } catch (Exception e) {
                    log.error("An error occured while parsing query", e);
                    throw new RuntimeException("" + e.getMessage());
                }
            });
        }
        if (queryRequest.getDatabaseType() != null
                && !(queryRequest.getCurrentMicroservicetype().equals("authentication microservice") && queryRequest.isDefaultIdP())
                //&& (queryRequest.getDatabaseType().equalsIgnoreCase("sql")
                //		|| (!queryRequest.getDatabaseType().equalsIgnoreCase("sql")) && !parsedBody.equals("{}")&& queryRequest.getHttpMethod().equalsIgnoreCase("post"))
                && StringUtils.isNotBlank(parsedBody)) {
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(parsedBody);
            finalQuery.put(QUERY, bodyParser(queryRequest, json, values));
        }
        Map<String, String> paramsMap = new HashMap<>();
        paramsMap.forEach((k, v) -> {
            String key = "%" + k;
            finalQuery.put(QUERY, finalQuery.get(QUERY).replaceAll(key, v));
        });

        return finalQuery;
    }

    /**
     * Get All Path Variables Values
     *
     * @param queryRequest
     * @param req
     * @param paths
     * @param map,         contains all values for Path Variables
     */
    private String getQuery(HttpServletRequest req, RuntimeQueryRequest queryRequest, List<String> values, List<String> paths) {
        RuntimeQueryRequest queryRequest1 = queryRequest;
        String theQuery = queryRequest.getQuery().replace("%", "");
        queryRequest1.setQuery(formatQuery1(queryRequest.getQuery()));
        String q = req.getHeader("q");
        String operation = "";
        List<String> params = new ArrayList<String>(Arrays.asList(formatQuery(q).split(",")));
        if (params.size() == 1) {
            paths = new ArrayList<String>(Arrays.asList(params.get(0).split(":")));
            if (paths.size() == 2) {
                if (StringUtils.isNumeric(values.get(1))) {
                    theQuery = "{" + paths.get(0) + ":" + values.get(1) + "}}";
                } else {
                    theQuery = "{'" + paths.get(0) + "':'" + values.get(1) + "'}}";
                }
            } else if (paths.size() > 2) {
                theQuery = "{" + paths.get(0) + ":{" + paths.get(1) + ":" + values.get(1) + "}}";
            }
        } else if (params.size() > 1) {
            paths = new ArrayList<String>(Arrays.asList(params.get(0).split(":")));
            operation = paths.get(0);
            theQuery = theQuery.replaceAll("operation", operation.substring(1));
            theQuery = theQuery.replaceAll("attribute1", paths.get(1));
            theQuery = theQuery.replaceAll("path1", values.get(1));
            int indexFirstElement = Integer.parseInt(paths.get(2));

            paths = new ArrayList<String>(Arrays.asList(params.get(1).split(":")));
            int indexSecondElement = Integer.parseInt(paths.get(1));
            if (indexSecondElement < indexFirstElement) {
                theQuery = theQuery.replaceAll("attribute2", paths.get(0));
                theQuery = theQuery.replaceAll("path2", values.get(2));
            } else {
                theQuery = theQuery.replaceAll("attribute2", paths.get(0));
                theQuery = theQuery.replaceAll("path2", values.get(2));
            }
        }
        return theQuery;
    }

    private List<String> getPathVariables(RuntimeQueryRequest queryRequest, String servletPath,
                                          Map<String, String> map) {
        // 16 is more then length of "/runtime/query/{containerId} to get the path of
        // the API after the ContainerID
        List<String> receivedPath = Arrays
                .asList(servletPath.substring(servletPath.indexOf('/', 16)).substring(1).split("/"));
        List<String> resspath = Arrays.asList(queryRequest.getPath().substring(1).split("/"));

        int index = 0;
        for (String part : resspath) {
            if (part.contains("{")) {
                map.put(part.replace("{", "").replace("}", ""), receivedPath.get(index));
            }
            index++;
        }
        return receivedPath;
    }

    /**
     * Parse the query and fill the current user members if they exist
     *
     * @param req
     * @param query
     * @param mCliet
     * @param dbName
     * @return the query after members fill
     */
    public String parseSessionParams(RuntimeQueryRequest req, String query, MongoClient mCliet, String dbName) {
        return this.sessionParamsHandler.handle(req.getUsername(), query, mCliet, dbName);
    }

    /**
     * Replace all params with their corresponding values
     *
     * @param req
     * @param finalQuery
     * @param map
     * @param headersMap
     * @param param
     * @param values
     * @throws Exception
     */
    private void setQueryParameters(RuntimeQueryRequest queryRequest, HttpServletRequest req,
                                    Map<String, String> finalQuery, Map<String, String> map, Map<String, List<String>> headersMap,
                                    RuntimeResourceParameter param, Map<String, String> values) throws Exception {
        String key = "%" + param.getName();
        String reqValue = null;
        if (param.getIn().equalsIgnoreCase(QUERY)) {
            reqValue = convertToString(req.getParameter(param.getName()), queryRequest);
            values.put(param.getName(), reqValue);
        } else if (param.getIn().equalsIgnoreCase("path")) {
            reqValue = convertToString(map.get(param.getName()), queryRequest);
            values.put(param.getName(), reqValue);
        } else if (param.getIn().equalsIgnoreCase("header")) {
            // Get Only the First Header Value
            reqValue = convertToString(headersMap.get(param.getName()).get(0), queryRequest);
            values.put(param.getName(), reqValue);
        } else if (param.getIn().equalsIgnoreCase("body")) {
            // Get Only the First Header Value
            reqValue = "";
        }
        if (reqValue == null) {
            reqValue = param.getValue();
        }
        if (reqValue == null) {
            finalQuery.put(QUERY, finalQuery.get(QUERY).replaceAll(key, ""));
        } else {
            finalQuery.put(QUERY, finalQuery.get(QUERY).replaceAll(key, reqValue));
        }
        if (!param.getType().equalsIgnoreCase("String")) {
            String valueToReplace = "\"" + req.getParameter(param.getName()) + "\"";
            if (reqValue == null) {
                finalQuery.put(QUERY, finalQuery.get(QUERY).replaceAll(key, null));
            } else {
                finalQuery.put(QUERY, finalQuery.get(QUERY).replaceAll(key, reqValue));
            }
            finalQuery.put(QUERY, finalQuery.get(QUERY).replaceAll(valueToReplace, req.getParameter(param.getName())));
        }
    }

    public void setSessionParamsHandler(SessionParamsHandler sessionParamsHandler) {
        this.sessionParamsHandler = sessionParamsHandler;
    }

    public String formatQuery(String content) {
        return content.replace("{", "").replace("[", "").replace("]", "").replace("}", "").replace("%", "");
    }

    public String formatQuery1(String content) {
        return content.replace("{", "").replace("[", "").replace("]", "").replace("}", "").replace("'", "");
    }

    public String collectApiParameters(String body, RuntimeQueryRequest queryRequest, HttpServletRequest req) {

        final String[] finalBody = new String[]{"{", "\"query\":{", "\"pathVariable\":{", "\"body\":",
                "\"header\":{"};
        HashMap<String, String> pathVariablesMap = new HashMap<String, String>();

        this.getPathVariables(queryRequest, req.getServletPath(), pathVariablesMap);
        Map<String, List<String>> headersParamsMap = Collections.list(req.getHeaderNames()).stream()
                .collect(Collectors.toMap(Function.identity(), h -> Collections.list(req.getHeaders(h))));

        queryRequest.getParameters().forEach((p) -> {
            if (p.getIn().equalsIgnoreCase("query")) {
                if (p.getType().equalsIgnoreCase("string")) {
                    finalBody[1] += "\"" + p.getName() + "\":\"" + req.getParameter(p.getName()) + "\",";
                } else {
                    finalBody[1] += "\"" + p.getName() + "\":" + req.getParameter(p.getName()) + ",";

                }

            } else if (p.getIn().equalsIgnoreCase("path")) {
                if (p.getType().equalsIgnoreCase("string")) {
                    finalBody[2] += "\"" + p.getName() + "\":\"" + pathVariablesMap.get(p.getName()) + "\",";
                } else {
                    finalBody[2] += "\"" + p.getName() + "\":" + pathVariablesMap.get(p.getName()) + ",";

                }

            } else if (p.getIn().equalsIgnoreCase("header")) {
                if (p.getType().equalsIgnoreCase("string")) {
                    finalBody[4] += "\"" + p.getName() + "\":\"" + headersParamsMap.get(p.getName()).get(0) + "\",";
                } else {
                    finalBody[4] += "\"" + p.getName() + "\":" + headersParamsMap.get(p.getName()).get(0) + ",";

                }

            }

        });

        if (finalBody[1].length() > 9) {
            finalBody[1] = finalBody[1].substring(0, finalBody[1].length() - 1);
        }
        if (finalBody[2].length() > "\"pathVariable:{\"".length()) {
            finalBody[2] = finalBody[2].substring(0, finalBody[2].length() - 1);
        }
        if (finalBody[4].length() > "\"header:{\"".length()) {
            finalBody[4] = finalBody[4].substring(0, finalBody[4].length() - 1);
        }
        if (body != null && !body.isEmpty()) {
            finalBody[3] = finalBody[3].concat(body);
        } else if (body == null) {
            finalBody[3] = finalBody[3].concat("{}");
        }

        finalBody[1] = finalBody[1].concat("},");
        finalBody[2] = finalBody[2].concat("},");
        finalBody[4] = finalBody[4].concat("},");

        finalBody[0] = finalBody[0].concat(finalBody[1]).concat(finalBody[2]).concat(finalBody[4]).concat(finalBody[3])
                .concat("}");
        return finalBody[0];

    }

    @SuppressWarnings("unused")
    private String bodyParser(RuntimeQueryRequest queryRequest, JSONObject json, Map<String, String> values)
            throws Exception {
        String likeClause = "";
        String query = queryRequest.getQuery().replaceAll("\n", "").replaceAll(" =", "=").replaceAll("= ", "=")
                .replaceAll("% ", "%").replaceAll(",", " , ").replaceAll(";", " ;").concat(" ");
        if (query.contains("like")) {
            likeClause = StringUtils.substringBetween(query, "'", "'");
            if (query.indexOf("like") < query.indexOf(likeClause))
                query = query.replaceAll(likeClause, "likeClause");
        }
        while (query.contains("%")) {
            String key = StringUtils.substringBetween(query, "%", " ").replaceAll("\"", "").replaceAll("}", "").replaceAll("]", "");
            if (values.keySet().contains(key)) {
                query = query.replaceAll("%" + key, values.get(key));
            }
            if (!query.contains("%"))
                break;
            String jsonValue = jsonParser(queryRequest, json, key);
            boolean isArray = jsonValue != null && jsonValue.startsWith("[");
            query = query.replaceAll((isArray ? "\"" : "") + "%" + key + (isArray ? "\"" : ""), jsonValue);
            if (!(json.get(key) instanceof String)) {
                String valueToReplace = "\"" + jsonValue + "\"";
                query = query.replaceAll(valueToReplace, jsonValue);
            }
        }
        query = query.replaceAll("likeClause", likeClause);
        return query;
    }

    private String jsonParser(RuntimeQueryRequest queryRequest, JSONObject json, String params) throws Exception {
        JSONParser parser = new JSONParser();
        System.out.println(params);
        List<String> keys = Arrays.asList(params.split("\\."));
        if (json.containsKey(keys.get(keys.size() - 1))) {

            if (queryRequest.getDatabaseType().equalsIgnoreCase("sql") && StringUtils.isAlpha(json.get(keys.get(keys.size() - 1)).toString())) {
                return "'" + json.get(keys.get(keys.size() - 1)).toString() + "'";
            } else {
                return json.get(keys.get(keys.size() - 1)).toString();
            }
        } else
            throw new Exception("json provided is missing " + keys.get(keys.size() - 1) + " key !");
    }

    public static boolean isAlphaNumeric(String s) {
        return p.matcher(s).find();
    }

    public static boolean isAlpha(String s) {
        return s != null && s.matches("^[a-zA-Z]*$");
    }

    private String convertToString(String value, RuntimeQueryRequest req) {
        if (req.getDatabaseType() != null && req.getDatabaseType().toLowerCase().equals("sql") && !StringUtils.isNumeric(value)) {
            return value = "'" + value + "'";
        }
        return value;
    }

}
