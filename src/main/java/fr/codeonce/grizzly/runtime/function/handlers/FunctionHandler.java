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
package fr.codeonce.grizzly.runtime.function.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import fr.codeonce.grizzly.common.runtime.*;
import fr.codeonce.grizzly.runtime.service.cache.CryptoHelper;
import fr.codeonce.grizzly.runtime.service.feign.DBSource;
import fr.codeonce.grizzly.runtime.service.feign.FeignDiscovery;
import fr.codeonce.grizzly.runtime.service.feign.FeignDiscoveryFunction;
import fr.codeonce.grizzly.runtime.service.query.handlers.QueryUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class FunctionHandler {

    @Autowired
    FeignDiscoveryFunction feignDiscovery;

    @Autowired
    FeignDiscovery feignDiscoveryDB;

    @Autowired
    QueryUtils queryUtils;

    @Autowired
    MongoFunctionHandler mongoFunctionHandler;

    @Autowired
    ElasticFunctionHandler elasticFunctionHandler;

    @Autowired
    SqlFunctionHandler sqlFunctionHandler;

    @Autowired
    @Qualifier("runtimeCryptoHandler")
    private CryptoHelper cryptoHelper;

    private static final Logger log = LoggerFactory.getLogger(FunctionHandler.class);

    public Object handleFunction(HttpServletRequest req, RuntimeQueryRequest queryRequest, String parsedBody,
                                 Object persistanceObject, String executionType, DBSource dbSource, boolean[] isOutArray) throws Exception {
        // the final response
        Response result = null;
        String dbName = null;
        String uri = null;

        // indicate if there is an attached function
        boolean isThereFunction = false;

        // prepare db connection for predefined function
        if (queryRequest.getFunctions() == null || queryRequest.getFunctions().isEmpty()) {
            dbName = (dbSource.getConnectionMode().toString().equalsIgnoreCase("FREE")) ? dbSource.getPhysicalDatabase()
                    : dbSource.getDatabase();
            uri = constructUri(dbSource);
        }

        // execute or invoke the function based on the type
        Optional<Object> executeInOutFunciton = invokeOrExecuteInOutFunciton(req, queryRequest, parsedBody,
                executionType, isOutArray);

        // check if there is there is a function that has been executed or invoked
        if (executeInOutFunciton.isPresent()) {
            isThereFunction = true;
            result = (Response) executeInOutFunciton.get();
        }

        if (executionType.equalsIgnoreCase("in")
                && ((queryRequest.getInFunctions() != null && !queryRequest.getInFunctions().isEmpty()))) {

            log.info("Handle the function with ID: {} as INPUT Function", queryRequest.getInFunctions().get(0).getId());

            // check the return type
            if (parsedBody.startsWith("[")) {
                isOutArray[0] = true;
            } else {
                isOutArray[0] = false;
            }

            log.info("Send a Request to handle the function with ID: {} as INPUT Function",
                    queryRequest.getInFunctions().get(0).getId());

            if (!queryRequest.getInFunctions().get(0).getLanguage().equalsIgnoreCase("AWS Lambda")) {
                result = sendFunctionExecuteRequest(req, queryRequest, parsedBody, executionType, uri, dbName,
                        queryRequest.getCollectionName(), dbSource.getProvider().toString(), executionType);

            } else {
                result = sendAWSLambdaExecuteRequeust(req, queryRequest, parsedBody, null, executionType);
            }

            isThereFunction = true;

        } else if (executionType.equalsIgnoreCase("out") && queryRequest.getOutFunctions() != null
                && !queryRequest.getOutFunctions().isEmpty()) {

            log.info("Handle the function execution with ID: {} as OUTPUT Function",
                    queryRequest.getOutFunctions().get(0).getId());

            // checking the type of the persistanceObject
            if (persistanceObject instanceof String string && string == "[]") {
                persistanceObject = new ArrayList<Document>();
            }
            if (persistanceObject instanceof Response) {
                return persistanceObject;
            }
            if (persistanceObject instanceof List<?> && dbSource.getProvider().toString().equals("MONGO")) {
                persistanceObject = documentToJson((List<Document>) persistanceObject, isOutArray[0]);
            } else if (persistanceObject instanceof Document document) {
                persistanceObject = document.toJson();
            } else if (persistanceObject instanceof String string && dbSource.getProvider().equals("ELASTICSEARCH.ON-PREMISE")
                    && !queryRequest.getHttpMethod().equalsIgnoreCase("GET")) {
                persistanceObject = elasticFunctionHandler.fetchElasticEntity(string, queryRequest);

            } else {
                ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();

                try {
                    persistanceObject = ow.writeValueAsString(persistanceObject);
                } catch (JsonProcessingException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            log.info("Send a Request to execute the function with ID: {} as OUTPUT Function",
                    queryRequest.getOutFunctions().get(0).getId());

            if (!queryRequest.getOutFunctions().get(0).getLanguage().equalsIgnoreCase("AWS Lambda")) {
                result = sendFunctionExecuteRequest(req, queryRequest, parsedBody, (String) persistanceObject, uri,
                        queryRequest.getCollectionName(), dbName, dbSource.getProvider().toString(), executionType);

            } else {
                result = sendAWSLambdaExecuteRequeust(req, queryRequest, parsedBody, (String) persistanceObject,
                        executionType);
            }

            isThereFunction = true;

        }

        return isThereFunction ? dispatchFunctionHandler(queryRequest, executionType, result) : persistanceObject;

    }

    private String constructUri(DBSource dbSource) {
        String uri = null;
        cryptoHelper.decrypt(dbSource);
        if (dbSource.getProvider().toString().equalsIgnoreCase("MONGO")) {
            if (dbSource.getConnectionMode().equalsIgnoreCase("ON-PREMISE")) {
                if (dbSource.isSecured()) {
                    String password = String.valueOf(dbSource.getPassword());
                    uri = "mongodb://" + dbSource.getUsername() + ":" + password + "@" + dbSource.getHost() + ":"
                            + dbSource.getPort();
                } else {
                    uri = "mongodb://" + dbSource.getHost() + ":" + dbSource.getPort();

                }
            } else if (dbSource.getProvider().equals("CLOUD")) {
                uri = dbSource.getUri();

            }
            return uri;

        }

        return null;

    }

    private Response sendAWSLambdaExecuteRequeust(HttpServletRequest req, RuntimeQueryRequest queryRequest,
                                                  String parsedBody, String persistanceObjectString, String executionType) throws Exception {

        AWSLambdaRequest awsLambdaExecuteRequest = new AWSLambdaRequest();
        String jsonRequestObject = queryUtils.collectApiParameters(parsedBody, queryRequest, req);
        awsLambdaExecuteRequest.setPayload(jsonRequestObject);

        if (executionType.equalsIgnoreCase("inOut")) {
            awsLambdaExecuteRequest.setFunctionName(queryRequest.getFunctions().get(0).getAwsFunctionName());
            awsLambdaExecuteRequest.setAwsCredentials(queryRequest.getFunctions().get(0).getAwsCredentials());
            awsLambdaExecuteRequest.setType("inOut");

        } else if (executionType.equalsIgnoreCase("in")) {
            awsLambdaExecuteRequest.setFunctionName(queryRequest.getInFunctions().get(0).getAwsFunctionName());
            awsLambdaExecuteRequest.setAwsCredentials(queryRequest.getInFunctions().get(0).getAwsCredentials());
            awsLambdaExecuteRequest.setType("in");
        } else {
            awsLambdaExecuteRequest.setType("out");
            awsLambdaExecuteRequest.setFunctionName(queryRequest.getOutFunctions().get(0).getAwsFunctionName());
            awsLambdaExecuteRequest.setAwsCredentials(queryRequest.getOutFunctions().get(0).getAwsCredentials());
            awsLambdaExecuteRequest
                    .setPayload(addPersistanceObjectToRequest(jsonRequestObject, persistanceObjectString));

        }

        return feignDiscovery.invoke(awsLambdaExecuteRequest);

    }

    private Response sendOpenFaasExecuteRequeust(HttpServletRequest req, RuntimeQueryRequest queryRequest,
                                                 String parsedBody, String persistanceObjectString, String executionType) throws Exception {

        OpenFaasRequest openFaasRequest = new OpenFaasRequest();
        System.out.println("Runtime open faas uri" + (queryRequest.getFunctions().get(0).getOpenFaasURI()));
        openFaasRequest.setUri(queryRequest.getFunctions().get(0).getOpenFaasURI());
        openFaasRequest.setBody(parsedBody);
        return feignDiscovery.invokeOpenFaas(openFaasRequest);

    }

    private String addPersistanceObjectToRequest(String parsedBody, String persistanceObjectString) {
        StringBuilder stringBuilder = new StringBuilder(parsedBody.substring(0, parsedBody.length() - 1));
        return stringBuilder.append(", \"grizzlyOutput\":").append(persistanceObjectString).append("}").toString();

    }

    // prepare and send a request to execute the function
    private Response sendFunctionExecuteRequest(HttpServletRequest req, RuntimeQueryRequest queryRequest,
                                                String parsedBody, String persistanceObjectString, String dbURI, String database, String collectionName,
                                                String provider, String executionType) throws Exception {

        FunctionsExecuteRequest functionsExecuteRequest = new FunctionsExecuteRequest();
        String jsonRequestObject = queryUtils.collectApiParameters(parsedBody, queryRequest, req);
        functionsExecuteRequest.setJsonRequestModel(jsonRequestObject);
        functionsExecuteRequest.setCollectionName(collectionName);
        functionsExecuteRequest.setProvider(provider);
        functionsExecuteRequest.setDbName(database);
        functionsExecuteRequest.setMongoURI(dbURI);
        functionsExecuteRequest.setLogId(queryRequest.getPath());

        if (executionType.equalsIgnoreCase("inOut")) {

            if (queryRequest.getFunctions().get(0).getLanguage().equalsIgnoreCase("java")
                    && queryRequest.getRequestModels() != null) {
                functionsExecuteRequest
                        .setLastUpdatRequestModel(queryRequest.getRequestModels().get(0).getLastUpdate() / 1000);
                functionsExecuteRequest.setPojoRequestModel(queryRequest.getRequestModels().get(0).getRequestModel());
            } else if (queryRequest.getFunctions().get(0).getLanguage().equalsIgnoreCase("java")
                    && queryRequest.getRequestModels() == null) {

                throw new Exception("please insert the request model ! ");
            }

            functionsExecuteRequest.setJsonPersistanceModel(persistanceObjectString);
            functionsExecuteRequest.setFunction(queryRequest.getFunctions().get(0));
            functionsExecuteRequest.setExecutionType("inOut");

        } else if (executionType.equalsIgnoreCase("in")) {
            if (queryRequest.getInFunctions().get(0).getLanguage().equalsIgnoreCase("java")
                    && queryRequest.getRequestModels() != null) {
                functionsExecuteRequest
                        .setLastUpdatRequestModel(queryRequest.getRequestModels().get(0).getLastUpdate() / 1000);
                functionsExecuteRequest.setPojoRequestModel(queryRequest.getRequestModels().get(0).getRequestModel());
            } else if (queryRequest.getInFunctions().get(0).getLanguage().equalsIgnoreCase("java")
                    && queryRequest.getRequestModels() == null) {
                throw new Exception("please insert the request model ! ");
            }

            functionsExecuteRequest.setJsonPersistanceModel(persistanceObjectString);
            functionsExecuteRequest.setFunction(queryRequest.getInFunctions().get(0));
            functionsExecuteRequest.setExecutionType("in");
        } else {

            if (queryRequest.getOutFunctions().get(0).getLanguage().equalsIgnoreCase("java")) {
                if (queryRequest.getRequestModels() != null) {
                    functionsExecuteRequest
                            .setLastUpdatRequestModel(queryRequest.getRequestModels().get(0).getLastUpdate());
                    functionsExecuteRequest
                            .setPojoRequestModel(queryRequest.getRequestModels().get(0).getRequestModel());

                } else {
                    throw new Exception("please insert the request model ! ");

                }

            }
            functionsExecuteRequest.setFunction(queryRequest.getOutFunctions().get(0));
            functionsExecuteRequest.setExecutionType("out");
            functionsExecuteRequest.setJsonPersistanceModel(persistanceObjectString);

        }

        return feignDiscovery.execute(functionsExecuteRequest);
    }

    private String documentToJson(List<Document> result, boolean isArray) {
        if (result.isEmpty()) {
            return "[]";
        }

        // convert List<Document> to List<String> to be passed as argument
        List<String> jsonArray = result.stream().map(f -> f.toJson()).collect(Collectors.toList());
        return "[" + String.join(",", jsonArray) + "]";

    }

    private Object dispatchFunctionHandler(RuntimeQueryRequest queryRequest, String executionType, Response result)
            throws Exception {

        if (queryRequest.getFunctions() != null && !queryRequest.getFunctions().isEmpty()) {
            return mongoFunctionHandler.mongoOutResponseHandler(result);

        }
        if (String.valueOf(queryRequest.getDatabaseType()).equals("sql")) {
            if (executionType.equalsIgnoreCase("in")) {
                return sqlFunctionHandler.sqlInResponseHandler(result);
            } else {
                return sqlFunctionHandler.sqlOutResponseHandler(result);

            }

        }

        switch (queryRequest.getProvider()) {
            case MONGO:
                if (executionType.equalsIgnoreCase("in")) {
                    return mongoFunctionHandler.mongoInResponseHandler(result);
                } else if (executionType.equalsIgnoreCase("out")) {
                    return mongoFunctionHandler.mongoOutResponseHandler(result);
                } else {
                    return null;
                }

            case COUCHDB:
                return null;

            case ELASTICSEARCH:
                if (executionType.equalsIgnoreCase("in")) {
                    return elasticFunctionHandler.elasticInResponseHandler(result);
                } else if (executionType.equalsIgnoreCase("out")) {
                    return elasticFunctionHandler.elasticOutResponseHandler(result, queryRequest);
                } else {
                    return null;
                }

            case MARKLOGIC:
                return null;

            default:
                return null;
        }

    }

    private Optional<Object> invokeOrExecuteInOutFunciton(HttpServletRequest req, RuntimeQueryRequest queryRequest,
                                                          String parsedBody, String executionType, boolean[] isOutArray) throws Exception {
        if ((queryRequest.getFunctions() != null && !queryRequest.getFunctions().isEmpty())) {
            log.info("Handle the function execution with ID: {} as  Function",
                    queryRequest.getFunctions().get(0).getId());
            if (parsedBody.startsWith("[")) {
                isOutArray[0] = true;
            } else {
                isOutArray[0] = false;
            }

            log.info("Send a Request to execute the function with ID: {} as  Function",
                    queryRequest.getFunctions().get(0).getLanguage());
            if (queryRequest.getFunctions().get(0).getLanguage().equals("OpenFaas")) {
                return Optional
                        .ofNullable(sendOpenFaasExecuteRequeust(req, queryRequest, parsedBody, null, executionType));
            }
            if (!queryRequest.getFunctions().get(0).getLanguage().equalsIgnoreCase("AWS Lambda")) {
                return Optional.ofNullable(sendFunctionExecuteRequest(req, queryRequest, parsedBody, null, null, null,
                        null, null, executionType));

            } else {
                return Optional
                        .ofNullable(sendAWSLambdaExecuteRequeust(req, queryRequest, parsedBody, null, executionType));
            }

        }
        return Optional.empty();
    }

    public Object getInFunction(HttpServletRequest req, RuntimeQueryRequest queryRequest) throws Exception {

        if (queryRequest.getInFunctions() != null && queryRequest.getInFunctions().size() > 0
                && queryRequest.getInFunctions().get(0) != null) {
            Response result = sendFunctionExecuteRequest(req, queryRequest, "{}", null, null, null,
                    queryRequest.getCollectionName(), null, "in");

            if (result.getType().equalsIgnoreCase("valid") && result.getResponse() instanceof String) {
                return result.getResponse();
            } else if (result.getType().equalsIgnoreCase("valid") && !(result.getResponse() instanceof String)) {
                result.setResponse("not valid query !");
                return result;
            } else {
                return result;
            }

        }

        return null;
    }

}
