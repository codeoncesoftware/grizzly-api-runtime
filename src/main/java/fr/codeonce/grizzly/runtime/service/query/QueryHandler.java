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
package fr.codeonce.grizzly.runtime.service.query;

import com.mongodb.client.MongoClient;
import fr.codeonce.grizzly.common.runtime.Provider;
import fr.codeonce.grizzly.common.runtime.RuntimeQueryRequest;
import fr.codeonce.grizzly.common.runtime.SecurityApiConfig;
import fr.codeonce.grizzly.common.runtime.resource.RuntimeResourceParameter;
import fr.codeonce.grizzly.runtime.function.handlers.FunctionHandler;
import fr.codeonce.grizzly.runtime.service.cache.MongoConnector;
import fr.codeonce.grizzly.runtime.service.cache.SqlConnector;
import fr.codeonce.grizzly.runtime.service.feign.DBSource;
import fr.codeonce.grizzly.runtime.service.feign.FeignDiscovery;
import fr.codeonce.grizzly.runtime.service.query.authentication.*;
import fr.codeonce.grizzly.runtime.service.query.handlers.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class QueryHandler {

    private static final String QUERY = "query";
    private static final String IDENTITYPROVIDER = "identityProvider";

    @Autowired
    private SignInHandler signInHandler;

    @Autowired
    private SignUpHandler signUpHandler;

    @Autowired
    private RolesHandler rolesHandler;

    @Autowired
    private UpdateUserHandler updateUserHandler;

    @Autowired
    private AllUsersHandler allUsersHandler;

    @Autowired
    private FeignDiscovery feignDiscovery;

    // NEED TO BE CALLED WITH FEIGN
    // @Autowired
    // private StaticResourceService resourceService;

    // @Value("${spring.data.mongodb.uri}")
    // private String atlasUri;

    @Value("${frontUrl}")
    private String frontUrl;

    @Value("${grizzly.client_id}")
    private String grizzlyPredefinedClientId;

    @Value("${grizzly.client_secret}")
    private String grizzlyPredefinedClientSecret;

    @Autowired
    private MongoHandler mongoHandler;

    @Autowired
    private HttpHandler httpHandler;

    @Autowired
    private CouchHandler couchHandler;

    @Autowired
    private ElasticHandler elasticHandler;

    @Autowired
    private MarklogicHandler markHandler;

    @Autowired
    private MongoConnector mongoConnector;

    @Autowired
    private FunctionHandler functionHandler;

    @Autowired
    private SqlConnector sqlConnector;

    @Autowired
    private SqlHandler sqlHandler;

    @Autowired
    private OauthHandler oauthHandler;

    private static final Logger log = LoggerFactory.getLogger(QueryHandler.class);

    public Object handleQuery(RuntimeQueryRequest queryRequest, String containerId, HttpServletRequest req,
                              HttpServletResponse res) throws Exception {

        MongoClient mClient = null;
        String databaseName = "";
        String dbURI = "";
        DBSource dbSource = null;
        if (queryRequest.getDbsourceId() != null
                && (queryRequest.getFunctions() == null || queryRequest.getFunctions().isEmpty())) {
            dbSource = feignDiscovery.getDBSource(queryRequest.getDbsourceId());
        }
        boolean[] isOutArray = new boolean[1];
        boolean in = false;
        boolean out = false;
        boolean inout = false;
        String type = "";
        if (queryRequest.getInFunctions() != null) {
            in = !queryRequest.getInFunctions().isEmpty();
            type = "in";
        }
        if (queryRequest.getOutFunctions() != null) {
            out = !queryRequest.getOutFunctions().isEmpty();
            type = "out";
        }
        if (queryRequest.getFunctions() != null) {
            inout = !queryRequest.getFunctions().isEmpty();
            type = "inOut";
        }
        // Boolean inOUt = !queryRequest.getInFunctions().isEmpty() ||
        // !queryRequest.getOutFunctions().isEmpty();
        if (queryRequest.getQueryType() != null) {
            if (queryRequest.getQueryType().equals("Execute") && (in || out || inout)) {
                String parsedBody = "{}";
                if (queryRequest.getHttpMethod().equalsIgnoreCase("POST")
                        || queryRequest.getHttpMethod().equalsIgnoreCase("PUT")) {
                    if (!queryRequest.getFunctions().get(0).getLanguage().equalsIgnoreCase("openfaas")) {
                        parsedBody = new HttpServletRequestWrapper(req).getReader().lines().reduce("",
                                (accumulator, actual) -> accumulator + actual);
                    } else {
                        parsedBody = req.getReader().lines().collect(Collectors.joining(System.lineSeparator()));

                    }
                    return functionHandler.handleFunction(req, queryRequest, parsedBody, null, type, dbSource,
                            isOutArray);

                } else {
                    if (queryRequest.getHttpMethod().equalsIgnoreCase("GET")
                            || queryRequest.getHttpMethod().equalsIgnoreCase("DELETE")) {
                        if (queryRequest.getFunctions().get(0).getLanguage().equalsIgnoreCase("openfaas")) {
                            parsedBody = req.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
                        }
                        return functionHandler.handleFunction(req, queryRequest, parsedBody, null, type, dbSource,
                                isOutArray);

                    }

                }

            }
        }
        if (queryRequest.getProvider() != null) {
            if (String.valueOf(queryRequest.getProvider()).equals(String.valueOf(Provider.MONGO))) {
                // Set The DB Name and The MongoClient Instance
                if (queryRequest.getConnectionMode().equalsIgnoreCase("FREE")) {
                    mClient = mongoConnector.getAtlasMongoClient();
                    databaseName = queryRequest.getPhysicalDatabaseName();

                } else {
                    mClient = mongoConnector.getMongoClient(queryRequest.getDbsourceId());
                    databaseName = feignDiscovery.getDatabaseFromProject(containerId);
                }
            }
        }
        Optional<RuntimeResourceParameter> paramDto = queryRequest.getParameters().stream()
                .filter(param -> param.getType().equalsIgnoreCase("file")).findFirst();

        if (paramDto.isPresent()) {
            return mongoHandler.handleFileSave(databaseName, paramDto.get().getName(), mClient, containerId, req);
        }

        if (req.getMethod().equalsIgnoreCase("get")
                || (queryRequest.getQueryType() != null && queryRequest.getQueryType().equalsIgnoreCase("Read"))) {
            if (queryRequest.getPath().equals("/allroles")) {
                return rolesHandler.handleAllRoles(queryRequest, databaseName, mClient, req, res, containerId);
            } else if (queryRequest.getPath().equals("/allusers")) {
                if (String.valueOf(queryRequest.getDatabaseType()).equals("sql")) {
                    return allUsersHandler.handleSqlAllUsers(queryRequest, databaseName, req);
                } else {
                    return allUsersHandler.handleAllUsers(queryRequest, databaseName, mClient, req);

                }
            } else if (queryRequest.getPath().equalsIgnoreCase("/userinfo")) {
                JSONObject json = oauthHandler.parser(queryRequest.getParsedQuery());
                if (json.get(IDENTITYPROVIDER) != null && json.get(IDENTITYPROVIDER) != "" && !queryRequest.getExistedIdentityProvidersName().isEmpty()
                        && queryRequest.getExistedIdentityProvidersName().contains(json.get(IDENTITYPROVIDER).toString().toUpperCase())) {
                    String token = req.getHeader("token");
                    return oauthHandler.handleUserinfo(res, token, json.get(IDENTITYPROVIDER).toString());
                } else {
                    res.resetBuffer();
                    res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    res.setHeader("Content-Type", "application/json");
                    res.getOutputStream()
                            .print("{\"errorMessage\":\"Please enter your identity provider name!\"}");
                    res.flushBuffer();
                    return null;
                }

            } else if (queryRequest.getPath().equalsIgnoreCase("/authorization")) {
                JSONObject json = oauthHandler.parser(queryRequest.getParsedQuery());

                if (json.get(IDENTITYPROVIDER) != null && !queryRequest.getExistedIdentityProvidersName().isEmpty()) {
                    String identityProvider = json.get(IDENTITYPROVIDER).toString();
                    if (queryRequest.getExistedIdentityProvidersName().contains(identityProvider.toUpperCase())) {
                        if (json.get("redirect_uri") != null) {
                            req.setAttribute("redirect_uri", json.get("redirect_uri"));
                        }
                        if (identityProvider.equalsIgnoreCase("KEYCLOAK") &&
                                json.get("username") != null && json.get("password") != null) {
                            return oauthHandler.handleAuthorization(req, res, queryRequest, containerId);
                        }
                        if (identityProvider.equalsIgnoreCase("KEYCLOAK") &&
                                (json.get("username") == null || json.get("password") == null)) {
                            res.resetBuffer();
                            res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                            res.setHeader("Content-Type", "application/json");
                            res.getOutputStream().print("{\"errorMessage\":\"Check your username and password !\"}");
                            res.flushBuffer();
                            return null;
                        }
                        if (identityProvider.equalsIgnoreCase("GOOGLE")) {
                            return oauthHandler.handleGoogleAuthorization(req, res);
                        }
                        if (identityProvider.equalsIgnoreCase("GITHUB")) {
                            return oauthHandler.handleGithubAuthorization(req, res);
                        }
                        if (identityProvider.equalsIgnoreCase("FACEBOOK")) {
                            return oauthHandler.handleFacebookAuthorization(req, res);
                        }
                        if (identityProvider.equalsIgnoreCase("LINKEDIN")) {
                            return oauthHandler.handleLinkedinAuthorization(req, res);
                        }
                        if (identityProvider.equalsIgnoreCase("GITLAB")) {
                            return oauthHandler.handleGitlabAuthorization(req, res);
                        }
                    } else {
                        res.resetBuffer();
                        res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        res.setHeader("Content-Type", "application/json");
                        res.getOutputStream().print(
                                "{\"errorMessage\":\"Your identity provider doesn't exist on this microservice !\"}");
                        res.flushBuffer();
                        return null;
                    }
                }

            } else if (queryRequest.getPath().equalsIgnoreCase("/jwk")) {
                String parsedQuery = queryRequest.getParsedQuery();

                JSONObject json = oauthHandler.parser(parsedQuery);
                if (json.get("client_id") != null && json.get("client_secret") != null) {
                    Optional<SecurityApiConfig> apiKeys = Optional.empty();
                    if (queryRequest.getAuthorizedApps() != null) {
                        apiKeys = queryRequest.getAuthorizedApps().stream()
                                .filter(f -> f.getClientId().equals(json.get("client_id"))
                                        && f.getSecretKey().equals(json.get("client_secret")))
                                .findAny();
                    }
                    if (apiKeys.isPresent() || (json.get("client_id").equals(grizzlyPredefinedClientId) &&
                            json.get("client_secret").equals(grizzlyPredefinedClientSecret))) {
                        return oauthHandler.handleJWKuri(containerId);
                    } else {
                        res.resetBuffer();
                        res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        res.setHeader("Content-Type", "application/json");
                        res.getOutputStream().print("{\"errorMessage\":\"Check your client_id and client_secret !\"}");
                        res.flushBuffer();
                        return null;
                    }
                }
            } else if (queryRequest.getPath().equalsIgnoreCase("/getRoles")) {
                JSONObject json = oauthHandler.parser(queryRequest.getParsedQuery());

                if (json.get("client_id") != null && json.get("client_secret") != null) {
                    Optional<SecurityApiConfig> apiKeys = Optional.empty();
                    if (queryRequest.getAuthorizedApps() != null) {
                        apiKeys = queryRequest.getAuthorizedApps().stream()
                                .filter(f -> f.getClientId().equals(json.get("client_id"))
                                        && f.getSecretKey().equals(json.get("client_secret")))
                                .findAny();
                    }
                    if (apiKeys.isPresent()) {
                        return oauthHandler.handlegetRoles(containerId);
                    } else {
                        res.resetBuffer();
                        res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        res.setHeader("Content-Type", "application/json");
                        res.getOutputStream().print("{\"errorMessage\":\"Check your client_id and client_secret !\"}");
                        res.flushBuffer();
                        return null;
                    }
                }
            } else {
                if (String.valueOf(queryRequest.getDatabaseType()).equals("sql")
                        && queryRequest.getPath().equals("/me")) {
                    return signInHandler.handleSQLMe(queryRequest, req, res);
                }
                if (queryRequest.getServiceURL() != null) {
                    return httpHandler.handleFindQuery(queryRequest.getServiceURL());
                } else {

                    Object persistanceObject = null;
                    Object customQuery = functionHandler.getInFunction(req, queryRequest);
                    if (String.valueOf(queryRequest.getDatabaseType()).equals("sql")) {
                        persistanceObject = sqlHandler.handleFindQuery(queryRequest, customQuery, req, res);
                    } else {
                        if (String.valueOf(queryRequest.getProvider()).equals(String.valueOf(Provider.MONGO))) {
                            persistanceObject = mongoHandler.handleFindQuery(queryRequest, databaseName, mClient,
                                    customQuery, req, res);
                        } else if (String.valueOf(queryRequest.getProvider())
                                .equals(String.valueOf(Provider.COUCHDB))) {
                            persistanceObject = couchHandler.handleFindQuery(queryRequest, req, res);
                        } else if (String.valueOf(queryRequest.getProvider())
                                .equals(String.valueOf(Provider.ELASTICSEARCH))) {
                            persistanceObject = elasticHandler.handleFindQuery(queryRequest, customQuery, req, res);
                        } else {
                            persistanceObject = markHandler.handleFindQuery(queryRequest, req, res);
                        }
                    }
                    return functionHandler.handleFunction(req, queryRequest, null, persistanceObject, "out", dbSource,
                            isOutArray);
                }

            }
        } else if (req.getMethod().equalsIgnoreCase("put") || req.getMethod().equalsIgnoreCase("post")) {
            String parsedBody = new HttpServletRequestWrapper(req).getReader().lines().reduce("",
                    (accumulator, actual) -> accumulator + actual);

            if (queryRequest.getPath().equals("/activate")) {
                if (String.valueOf(queryRequest.getDatabaseType()).equals("sql")) {
                    return updateUserHandler.activateUserSQL(queryRequest, req, res);
                } else {
                    return handleActivatePath(queryRequest, databaseName, mClient, req, res, parsedBody);

                }
            }

            // if (verifyJsonBody(parsedBody)) {
            if (queryRequest.getPath().equals("/signin")) {

                if (String.valueOf(queryRequest.getDatabaseType()).equals("sql")) {
                    return signInHandler.handleSQLSignIn(queryRequest, parsedBody, req, res, containerId);

                } else {
                    return signInHandler.handleSignIn(queryRequest, databaseName, mClient, req, res, containerId,
                            parsedBody);
                }

            } else if (queryRequest.getPath().equals("/signup")) {
                if (String.valueOf(queryRequest.getDatabaseType()).equals("sql")) {
                    return signUpHandler.handleSqlSignUp(queryRequest, parsedBody, req, res);
                } else {
                    return signUpHandler.handleSignUp(queryRequest, databaseName, mClient, req, res, parsedBody);

                }
            } else if (queryRequest.getPath().equals("/updateuser")) {
                if (String.valueOf(queryRequest.getDatabaseType()).equals("sql")) {
                    return sqlHandler.handleSQLUpdateUserQuery(queryRequest, parsedBody, res, req, res);
                } else {
                    return updateUserHandler.handleUpdateUser(queryRequest, databaseName, mClient, req, containerId,
                            parsedBody);
                }

            } else if (queryRequest.getPath().equals("/grant")) {
                if (String.valueOf(queryRequest.getDatabaseType()).equals("sql")) {
                    return rolesHandler.handleSQLGrantRoles(queryRequest, databaseName, req, res,
                            containerId, parsedBody);
                } else {
                    return rolesHandler.handleGrantRoles(queryRequest, databaseName, mClient, req, res, containerId,
                            parsedBody);
                }

            } else if (queryRequest.getQueryType() != null && queryRequest.getQueryType().equalsIgnoreCase("Insert")) {
                Object firstResult = functionHandler.handleFunction(req, queryRequest, parsedBody, null, "in", dbSource,
                        isOutArray);
                Object persistanceObject = null;
                if (String.valueOf(queryRequest.getProvider()).equals(String.valueOf(Provider.MONGO))) {
                    persistanceObject = dispatchInsertQuery(queryRequest, containerId, req, res, mClient, databaseName,
                            parsedBody, firstResult);
                }
                if (String.valueOf(queryRequest.getDatabaseType()).equals("sql")) {
                    persistanceObject = sqlHandler.handleInsertQuery(queryRequest, parsedBody, firstResult, req, res);
                }
                return functionHandler.handleFunction(req, queryRequest, parsedBody, persistanceObject, "out", dbSource,
                        isOutArray);

            } else if (queryRequest.getQueryType() != null && queryRequest.getQueryType().equalsIgnoreCase("Update")) {
                Object persistanceObject = null;
                Object firstResult = functionHandler.handleFunction(req, queryRequest, parsedBody, null, "in", dbSource,
                        isOutArray);
                if (String.valueOf(queryRequest.getProvider()).equals(String.valueOf(Provider.MONGO))) {
                    persistanceObject = mongoHandler.handleUpdateQuery(queryRequest, databaseName, mClient, req,
                            parsedBody, firstResult);
                }
                if (String.valueOf(queryRequest.getDatabaseType()).equals("sql")) {
                    persistanceObject = sqlHandler.handleUpdateQuery(queryRequest, parsedBody, firstResult, req, res);

                }
                return functionHandler.handleFunction(req, queryRequest, parsedBody, persistanceObject, "out", dbSource,
                        isOutArray);
            }
            // else {
            // return couchHandler.handleUpdateQuery(queryRequest, parsedBody, req, res);
            // }
            // } else {
            // res.setStatus(400);
            // return new Document("message", "Your JSON body is malformatted!");
            // }
        } else if (req.getMethod().equalsIgnoreCase("delete")) {

            if (queryRequest.getServiceURL() != null) {
                httpHandler.handleDeleteQuery(queryRequest, req);
            } else {
                if (String.valueOf(queryRequest.getDatabaseType()).equals("sql")) {
                    sqlHandler.handleDeleteQuery(queryRequest, req);
                } else {
                    if (String.valueOf(queryRequest.getProvider()).equals(String.valueOf(Provider.MONGO))) {
                        return mongoHandler.handleDeleteQuery(queryRequest, databaseName, mClient, req);
                    } else if (String.valueOf(queryRequest.getProvider()).equals(String.valueOf(Provider.COUCHDB))) {
                        couchHandler.handleDeleteQuery(queryRequest, req);
                    } else {
                        elasticHandler.handleDeleteQuery(queryRequest, req);
                    }
                }
            }

            return null;
        }
        // If no Method Selected
        return new ArrayList<>();

    }

    private Object dispatchInsertQuery(RuntimeQueryRequest queryRequest, String containerId, HttpServletRequest req,
                                       HttpServletResponse res, MongoClient mClient, String databaseName, String parsedBody, Object finalResult)
            throws Exception {
        if (String.valueOf(queryRequest.getDatabaseType()).equals("sql")) {
            return sqlHandler.handleInsertQuery(queryRequest, parsedBody, finalResult, req, res);
        } else {
            if (String.valueOf(queryRequest.getProvider()).equals(String.valueOf(Provider.MONGO))) {
                return mongoHandler.handleInsertQuery(queryRequest, databaseName, mClient, containerId, req, parsedBody,
                        finalResult);
            } else if (String.valueOf(queryRequest.getProvider()).equals(String.valueOf(Provider.COUCHDB))) {
                return couchHandler.handleInsertQuery(queryRequest, parsedBody, req, res, finalResult);
            } else {
                return elasticHandler.handleInsertQuery(queryRequest, parsedBody, req, res, finalResult);
            }
        }
    }

    private Object dispatchUpdateQuery(RuntimeQueryRequest queryRequest, HttpServletRequest req, MongoClient mClient,
                                       String databaseName, String parsedBody, Object finalResult) throws Exception {
        // if
        // (String.valueOf(queryRequest.getProvider()).equals(String.valueOf(Provider.MONGO)))
        // {

        return mongoHandler.handleUpdateQuery(queryRequest, databaseName, mClient, req, parsedBody, finalResult);
        // }
        // } else if
        // (String.valueOf(queryRequest.getProvider()).equals(String.valueOf(Provider.COUCHDB)))
        // {
        // return couchHandler.handleInsertQuery(queryRequest, parsedBody, req, res);
        // } else {
        // return elasticHandler.handleInsertQuery(queryRequest, parsedBody, req, res);
        // }
    }

    private Object handleActivatePath(RuntimeQueryRequest queryRequest, String databaseName, MongoClient mClient,
                                      HttpServletRequest req, HttpServletResponse res, String parsedBody) throws Exception {

        if (!StringUtils.isEmpty(parsedBody) && !verifyJsonBody(parsedBody)) {
            res.setStatus(400);
            return new Document("message", "Your JSON body is malformatted!");
        }
        return updateUserHandler.activate(queryRequest, databaseName, mClient, req, res);

    }

    // FIX the parseRequestBody Stream closed problem
    private boolean verifyJsonBody(String body) throws IOException {
        JSONParser parser = new JSONParser();
        try {
            parser.parse(body);
            return true;
        } catch (ParseException e) {
            log.debug("an error has occured {}", e);
            return false;
        }
    }

}
