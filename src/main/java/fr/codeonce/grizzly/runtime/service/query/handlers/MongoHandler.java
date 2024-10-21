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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mongodb.client.MongoClient;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import fr.codeonce.grizzly.common.runtime.Response;
import fr.codeonce.grizzly.common.runtime.RuntimeQueryRequest;
import fr.codeonce.grizzly.common.runtime.resource.RuntimeResourceParameter;
import fr.codeonce.grizzly.runtime.function.handlers.FunctionHandler;
import fr.codeonce.grizzly.runtime.function.handlers.MongoFunctionHandler;
import fr.codeonce.grizzly.runtime.service.cache.MongoConnector;
import fr.codeonce.grizzly.runtime.service.query.StaticResourceService;
import fr.codeonce.grizzly.runtime.util.DocumentHexIdHandler;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.codecs.BsonArrayCodec;
import org.bson.codecs.DecoderContext;
import org.bson.json.JsonReader;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class MongoHandler {

    private static final String QUERY = "query";

    @Autowired
    private MongoConnector mongoConnector;

    @Autowired
    private MappingJackson2HttpMessageConverter springMvcJacksonConverter;

    // NEED TO BE CALLED WITH FEIGN
    @Autowired
    private StaticResourceService resourceService;

    @Autowired
    private QueryUtils queryUtils;

    @Autowired
    private MongoFunctionHandler mongoFunctionHandler;

    @Autowired
    private FunctionHandler FunctionHandler;

    @Value("${frontUrl}")
    private String frontUrl;

    private static final Logger log = LoggerFactory.getLogger(MongoHandler.class);

    public Object handleFindQuery(RuntimeQueryRequest queryRequest, String databaseName, MongoClient mClient,
                                  Object customQuery, HttpServletRequest req, HttpServletResponse res) throws Exception {

        if (customQuery instanceof Response) {
            return customQuery;
        }
        if (queryRequest.getExecutionType().equalsIgnoreCase("FILE")) {
            try {
                return handleGetFile(mClient, databaseName, req, res);
            } catch (FileNotFoundException e) {
                throw new IllegalArgumentException("File not found");
            }
        }
        // The Query String as a MongoQuery Object
        Map<String, String> finalQuery = queryUtils.parseQuery(queryRequest, queryRequest.getQuery(), mClient,
                databaseName, req, queryRequest.getParsedQuery());
        // List for the Result Elements
        List<Document> fetchedResult = new ArrayList<>();
        List<Document> result = new ArrayList<>();
        BasicQuery query;

        if (mClient != null) {
            MongoTemplate mongoTemplate = new MongoTemplate(mClient, databaseName);

            // Get DB Query
            if (customQuery != null && customQuery instanceof String string) {

                try {
                    query = new BasicQuery(string);
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    Response response = new Response();
                    response.setHttpCode(400);
                    response.setType("exception");
                    response.setResponse("Please insert a valid query");
                    return response;
                }
            } else if (queryRequest.getQueryName() != null && queryRequest.getQueryName().equals("aggregation")) {
                query = null;
            } else {
                query = new BasicQuery(finalQuery.get(QUERY));
            }
            // Apply Action on One Or Many
            if (!queryRequest.isMany() && (queryRequest.getQueryName() == null || !queryRequest.getQueryName().equals("aggregation"))) { //queryname == null to avoid regression
                query.limit(1);
            }

            // Set Projections
            if (queryRequest.getFields() != null && !queryRequest.getFields().isEmpty()) {
                queryRequest.getFields().forEach(field -> {
                    if (!field.isEmpty())
                        query.fields().include(field);
                });
            }

            if (!queryRequest.isPageable()) {

                if (queryRequest.getQueryName() != null && queryRequest.getQueryName().equals("count")) {
                    if (!query.getQueryObject().equals(new Document())) {
                        if (queryRequest.getReturnType().equals("text/plain") || queryRequest.getReturnType().equals("text/html")) {
                            return mongoTemplate.count(query, queryRequest.getCollectionName());
                        }
                        return new Document("count", mongoTemplate.count(query, queryRequest.getCollectionName()));
                    } else {
                        if (queryRequest.getReturnType().equals("text/plain") || queryRequest.getReturnType().equals("text/html")) {
                            return mongoTemplate.getCollection(queryRequest.getCollectionName()).countDocuments();
                        }
                        return new Document("count", mongoTemplate.getCollection(queryRequest.getCollectionName()).countDocuments());
                    }
                } else if (queryRequest.getQueryName() != null && queryRequest.getQueryName().equals("aggregation")) {
                    boolean isArray = finalQuery.get("query") != null && finalQuery.get("query").startsWith("[");
                    JSONArray jsonArray = null;
                    String pipelineString = null;
                    if (isArray) {
                        jsonArray = new JSONArray(finalQuery.get("query"));
                        pipelineString = jsonArray.toString();
                    } else {
                        jsonArray = parser(finalQuery.get("query"));
                        pipelineString = jsonArray.toString().replaceAll("\\\\\"", "'").replaceAll("\"", "");
                    }

                    List<BsonDocument> pipeline = new BsonArrayCodec()
                            .decode(new JsonReader(pipelineString), DecoderContext.builder().build())
                            .stream().map(BsonValue::asDocument)
                            .collect(Collectors.toList());

                    mongoTemplate.getCollection(queryRequest.getCollectionName())
                            .aggregate(pipeline).spliterator().forEachRemaining(r -> {
                                if (r.get("_id") != null) {
                                    r.replace("_id", r.get("_id"), r.get("_id").toString());
                                }
                                result.add(r);
                            });
                } else {
                    mongoTemplate.executeQuery(query, queryRequest.getCollectionName(),
                            document -> result.add(DocumentHexIdHandler.transformMongoHexID(document)));
                }

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

                Pageable pageable = PageRequest.of(Integer.parseInt(req.getParameter("pageNumber")),
                        Integer.parseInt(req.getParameter("pageSize")));
                Query pageableQuery = null;
                if (queryRequest.getQueryName() == null || !queryRequest.getQueryName().equals("aggregation")) {
                    pageableQuery = query.with(pageable);
                }

                if (queryRequest.getQueryName() != null && queryRequest.getQueryName().equals("count")) {
                    if (!query.getQueryObject().equals(new Document())) {
                        result.add(new Document("count", mongoTemplate.count(pageableQuery, queryRequest.getCollectionName())));
                    } else {
                        result.add(new Document("count", mongoTemplate.getCollection(queryRequest.getCollectionName()).countDocuments()));
                    }
                } else if (queryRequest.getQueryName() != null && queryRequest.getQueryName().equals("aggregation")) {
                    boolean isArray = finalQuery.get("query") != null && finalQuery.get("query").startsWith("[");
                    JSONArray jsonArray = null;
                    String pipelineString = null;
                    if (isArray) {
                        jsonArray = new JSONArray(finalQuery.get("query"));
                        pipelineString = jsonArray.toString();
                    } else {
                        jsonArray = parser(finalQuery.get("query"));
                        pipelineString = jsonArray.toString().replaceAll("\\\\\"", "'").replaceAll("\"", "");
                    }

                    List<BsonDocument> pipeline = new BsonArrayCodec()
                            .decode(new JsonReader(pipelineString), DecoderContext.builder().build())
                            .stream().map(BsonValue::asDocument)
                            .collect(Collectors.toList());

                    mongoTemplate.getCollection(queryRequest.getCollectionName())
                            .aggregate(pipeline).spliterator().forEachRemaining(r -> {
                                if (r.get("_id") != null) {
                                    r.replace("_id", r.get("_id"), r.get("_id").toString());
                                }
                                result.add(r);
                            });
                } else {
                    mongoTemplate.executeQuery(pageableQuery, queryRequest.getCollectionName(),
                            document -> result.add(DocumentHexIdHandler.transformMongoHexID(document)));
                }

                // Result Page

                return PageableExecutionUtils
                        .getPage(result, pageable, () -> mongoTemplate.count(query, queryRequest.getCollectionName()))
                        .getContent();
            }
        }
        if (result.isEmpty()) {
            return queryRequest.isMany() ? new ArrayList<>() : convertToReturnType(queryRequest, null);
        }

        return !queryRequest.isMany() ? convertToReturnType(queryRequest, result.get(0))
                : convertToReturnType(queryRequest, result);

    }

    public Object handleInsertQuery(RuntimeQueryRequest queryRequest, String databaseName, MongoClient mongoClient,
                                    String containerId, HttpServletRequest req, String parsedBody, Object functionResponse) throws Exception {

        if (!queryRequest.getHttpMethod().equalsIgnoreCase("post")
                && !queryRequest.getHttpMethod().equalsIgnoreCase("put")) {
            return null;
        }

        // Check if it is a File Upload
        Optional<RuntimeResourceParameter> paramDto = queryRequest.getParameters().stream()
                .filter(param -> param.getType().equalsIgnoreCase("file")).findFirst();

        if (paramDto.isPresent()) {
            return handleFileSave(databaseName, paramDto.get().getName(), mongoClient, containerId, req);
        }

        List<Document> result = new ArrayList<>();
        List<Document> result2 = new ArrayList<>();

        // Parse Body
        parsedBody = queryUtils.parseQuery(queryRequest, parsedBody, mongoClient, databaseName, req, null).get(QUERY);

        if (mongoClient != null) {

            MongoTemplate mongoTemplate = new MongoTemplate(mongoClient, databaseName);

            // Test if the Given Query have Multiple Objects
            if (parsedBody.length() > 1) {

                // input function's section
                if (queryRequest.getInFunctions() != null && !queryRequest.getInFunctions().isEmpty()) {

                    if (functionResponse instanceof Response) {// return client exception
                        return functionResponse;
                    }
                    if (functionResponse instanceof Document document) {
                        result.add(DocumentHexIdHandler.transformMongoHexID(document));

                    } else if (functionResponse instanceof List<?> && !((ArrayList<?>) functionResponse).isEmpty()) {
                        if (((ArrayList<?>) functionResponse).get(0) instanceof Document)
                            ((List<Document>) functionResponse).forEach((d) -> {
                                result.add(d);
                            });

                    } else {

                        Response response = new Response();
                        response.setHttpCode(400);
                        response.setResponse("Invalid document to insert, please check your input function ");
                        response.setType("exception");
                        return response;
                    }

                }
                // database section
                if (parsedBody.substring(0, 1).equals("[")) {// if Array
                    if (result.size() == 0) {
                        JsonArray jsonArray = new JsonParser().parse(parsedBody).getAsJsonArray();
                        jsonArray.forEach(element -> {
                            String jsonToInsert = element.toString();
                            Document document = mongoTemplate.insert(Document.parse(jsonToInsert),
                                    queryRequest.getCollectionName());
                            result2.add(DocumentHexIdHandler.transformMongoHexID(document));

                        });
                    } else {
                        result.forEach((d) -> {
                            Document document = mongoTemplate.insert(d, queryRequest.getCollectionName());
                            result2.add(DocumentHexIdHandler.transformMongoHexID(document));
                        });
                    }

                } else {// if it's Object
                    if (result.size() == 0) {
                        Document document = mongoTemplate.insert(Document.parse(parsedBody),
                                queryRequest.getCollectionName());
                        result2.add(DocumentHexIdHandler.transformMongoHexID(document));
                    } else {
                        Document document = mongoTemplate.insert(result.get(0), queryRequest.getCollectionName());
                        result2.add(DocumentHexIdHandler.transformMongoHexID(document));
                    }

                }
            }

        }

        // return convertToReturnType(queryRequest, result2);
        return (!queryRequest.isMany()
                && (queryRequest.getOutFunctions() == null || queryRequest.getOutFunctions().isEmpty()))
                ? convertToReturnType(queryRequest, result2.get(0))
                : convertToReturnType(queryRequest, result2);

    }

    public Object handleUpdateQuery(RuntimeQueryRequest queryRequest, String databaseName, MongoClient mongoClient,
                                    HttpServletRequest req, String parsedBody, Object functionResponse) throws Exception {
        if (!queryRequest.getHttpMethod().equalsIgnoreCase("post")
                && !queryRequest.getHttpMethod().equalsIgnoreCase("put")) {
            return null;
        }
        UpdateResult updateResult = null;
        Object finalResult1 = null;
        Document queryBodyParsed = null;

        if (mongoClient != null) {
            MongoTemplate mongoTemplate = new MongoTemplate(mongoClient, databaseName);

            Map<String, String> finalQuery = queryUtils.parseQuery(queryRequest, queryRequest.getQuery(), mongoClient,
                    databaseName, req, null);

            BasicQuery basicQuery = new BasicQuery(finalQuery.get(QUERY));
            // Apply Action on One Or Many
            if (!queryRequest.isMany()) {
                basicQuery.limit(1);
            }

            parsedBody = this.queryUtils.parseSessionParams(queryRequest, parsedBody, mongoClient, databaseName);
            if (queryRequest.getInFunctions() != null && !queryRequest.getInFunctions().isEmpty()) {

                if (functionResponse instanceof Response) {
                    return functionResponse;
                }
                if (functionResponse instanceof Document) {
                    finalResult1 = functionResponse;

                } else {

                    Response response = new Response();
                    response.setHttpCode(400);
                    response.setResponse("Invalid document to insert, please check your input function ");
                    response.setType("exception");
                    return response;
                }

            }

            if (finalResult1 != null) {
                queryBodyParsed = (Document) finalResult1;
            } else {

                queryBodyParsed = Document.parse(parsedBody);
            }

            Update update = new Update();
            queryBodyParsed.forEach(update::set);

            ObjectMapper jsonWriter = new ObjectMapper();
            JsonNode jsonBody = jsonWriter.readTree(parsedBody);
            Query query = new Query();
            if (jsonBody.get("_id") != null) {
                query.addCriteria(new Criteria("_id").is(jsonBody.get("_id").asText()));
            }
            updateResult = mongoTemplate.updateFirst(query, update, queryRequest.getCollectionName());

        }
        return queryBodyParsed;

    }

    /**
     * @param resource
     * @param req
     * @param mongoClient
     * @throws IOException
     * @throws ServletException
     */
    public JsonNode handleFileSave(String databaseName, String fileName, MongoClient mongoClient, String containerId,
                                   HttpServletRequest req) throws IOException, ServletException {
        String resultId;
        String result = "";
        Part filePart = req.getPart(fileName);
        InputStream fileContent = filePart.getInputStream();

        if (mongoClient != null) {
            // Test if the Given Query have Multiple Objects
            GridFsTemplate gridFsTemplate = this.mongoConnector.getGridFs(mongoClient, databaseName);
            resultId = gridFsTemplate.store(fileContent, filePart.getSubmittedFileName()).toHexString();
            String fileUrl = frontUrl + "/runtime/static/" + containerId + "/" + resultId;
            // File URL in MetaData
            Document metaData = new Document();
            metaData.put("url", frontUrl + "/runtime/static/" + containerId + "/" + resultId);
            result = "{\"id\":\"" + resultId + "\", \"url\":\"" + fileUrl + "\"}";
        }
        return new ObjectMapper().readTree(result);
    }

    private GridFsResource handleGetFile(MongoClient mongoClient, String databaseName, HttpServletRequest req,
                                         HttpServletResponse res) throws FileNotFoundException {
        GridFsTemplate gridFsTemplate = this.mongoConnector.getGridFs(mongoClient, databaseName);
        GridFSFile file = gridFsTemplate.findOne(Query.query((Criteria.where("_id").is(req.getParameter("id")))));
        GridFsResource resource = gridFsTemplate.getResource(file);
        try {
            resourceService.setHttpServletResponse(resource.getInputStream().readAllBytes(), res);
        } catch (IOException e) {
            throw new FileNotFoundException();
        }
        return null;
    }

    public Object handleDeleteQuery(RuntimeQueryRequest queryRequest, String databaseName, MongoClient mongoClient,
                                    HttpServletRequest req) throws Exception {

        if (!queryRequest.getHttpMethod().equalsIgnoreCase("delete")) {
            return null;
        }
        if (queryRequest.getExecutionType().equalsIgnoreCase("FILE")) {
            handleDeleteFile(mongoClient, databaseName, req);
            return null;
        }

        Map<String, String> finalQuery = queryUtils.parseQuery(queryRequest, queryRequest.getQuery(), mongoClient,
                databaseName, req, null);
        DeleteResult remove = null;
        if (mongoClient != null) {
            MongoTemplate mongoTemplate = new MongoTemplate(mongoClient, databaseName);
            BasicQuery basicQuery = new BasicQuery(finalQuery.get(QUERY));
            if (!queryRequest.isMany()) {
                basicQuery.limit(1);
            }
            remove = mongoTemplate.remove(basicQuery, queryRequest.getCollectionName());
        }
        // output function's section
        if (remove.getDeletedCount() >= 0 && queryRequest.getOutFunctions() != null
                && !queryRequest.getOutFunctions().isEmpty()) {
            String insertedArguments = queryUtils.collectApiParameters(null, queryRequest, req);

            Object finalResult = null;

            // finalResult = mongoFunctionHandler.mongoOutExecuteFunction(queryRequest,
            // "{}", insertedArguments);

            if (finalResult != null && finalResult instanceof Response) {
                // in case of exception return response
                return finalResult;

            } else { // in case of valid output with unkonw type
                return finalResult;
            }
        }
        return null;

    }

    private void handleDeleteFile(MongoClient mongoClient, String databaseName, HttpServletRequest req) {
        BasicQuery query = new BasicQuery("{\"_id\":\"" + req.getParameter("id") + "\"}");
        this.mongoConnector.getGridFs(mongoClient, databaseName).delete(query);
    }

    private Object convertToReturnType(RuntimeQueryRequest req, Object obj) {
        if (req.getReturnType().toLowerCase().contains("xml")) {
            try {
                return XML
                        .toString(new JSONObject(springMvcJacksonConverter.getObjectMapper().writeValueAsString(obj)));
            } catch (JSONException | JsonProcessingException e) {
                return obj;
            }
        } else {
            return obj;
        }

    }

    public JSONArray parser(String value) {
        Gson gson = new Gson();
        com.google.gson.stream.JsonReader reader = new com.google.gson.stream.JsonReader(new StringReader(value));
        reader.setLenient(true);
        JsonObject jsonObject = gson.fromJson(reader, JsonObject.class);
        JSONArray jsonArray = new JSONArray();
        jsonObject.keySet().forEach(key -> {
            JSONObject obj = new JSONObject();
            obj.put(key, jsonObject.get(key).deepCopy());
            jsonArray.put(obj);
        });
        return jsonArray;
    }

}
