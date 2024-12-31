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
//package fr.codeonce.grizzly.runtime.test.query;
//
//import fr.codeonce.grizzly.runtime.AbstractRuntimeTest;
//
//public class QueryHandlerTest extends AbstractRuntimeTest {
//
////	@Spy
////	private MongoHandler mongoHandler = new MongoHandler();
////
////	@Spy
////	@InjectMocks
////	private QueryHandler queryHandler = new QueryHandler();
////
////	@Autowired
////	private SessionParamsHandler sessionParamsHandler;
////
////	@Autowired
////	private QueryUtils queryUtils;
////
////	@Before
////	public void init() {
////		queryUtils.setSessionParamsHandler(sessionParamsHandler);
////
////	}
//
////	// @Test
////	@SuppressWarnings("deprecation")
////	public void shouldSelectTheRightMethod() throws IOException, ServletException, ParseException {
////		MockitoAnnotations.initMocks(this);
////		// INIT
////		MockMultipartHttpServletRequest req = new MockMultipartHttpServletRequest();
////		RuntimeQueryRequest rQueryRequest = new RuntimeQueryRequest();
////		rQueryRequest.setProvider(Provider.MONGO);
////		fr.codeonce.grizzly.core.domain.resource.Resource resource = new fr.codeonce.grizzly.core.domain.resource.Resource();
////		fr.codeonce.grizzly.core.domain.resource.CustomQuery customQuery = new fr.codeonce.grizzly.core.domain.resource.CustomQuery();
////		customQuery.setDatabase("testDB");
////		customQuery.setDatasource("dbsourceId");
////		customQuery.setCollectionName("testCollectionName");
////		resource.setCustomQuery(customQuery);
////		doReturn(resource).when(containerResourceService).getResource("testId", "testPath");
////		Mockito.when(containerResourceService.getResource("testId", "testPath")).thenReturn(resource);
////		DBSourceDto dbsourceDto = new DBSourceDto();
////		doReturn(dbsourceDto).when(dbSourceService).getDbSourceById("dbsourceId");
////		MongoClient mongoClient = new MongoClient();
////		doReturn(mongoClient).when(mongoCacheService).getMongoClient(dbsourceDto);
////
////		req.setMethod("get");
////		queryHandler.handleQuery(rQueryRequest, "testId", req, null);
////
////		verify(mongoHandler, times(1)).handleFindQuery(rQueryRequest, "testDB", null, req, null);
////
////		req.setMethod("delete");
////		try {
////			queryHandler.handleQuery(rQueryRequest, "testId", req, null);
////		} catch (Exception e) {
////			assertThatIllegalStateException();
////		}
////
////		verify(mongoHandler, times(1)).handleDeleteQuery(rQueryRequest, "testDB", null, req);
////
////		req.setMethod("put");
////		resource.getCustomQuery().setType("insert");
////		queryHandler.handleQuery(rQueryRequest, "testId", req, null);
////		String parsedBody = new HttpServletRequestWrapper(req).getReader().lines().reduce("",
////				(accumulator, actual) -> accumulator + actual);
////
////		verify(mongoHandler, times(1)).handleInsertQuery(rQueryRequest, "testDB", null, null, req, parsedBody);
////
////		resource.getCustomQuery().setType("update");
////		req.setContent("{\"test\":\"test\"}".getBytes());
////		req.setContentType("application/json");
////		try {
////			queryHandler.handleQuery(rQueryRequest, "testId", req, null);
////		} catch (Exception e) {
////			assertThatIllegalStateException();
////		}
////
////		verify(mongoHandler, times(1)).handleUpdateQuery(rQueryRequest, "testDB", null, req, parsedBody);
////
////	}
//
////	@Test
////	public void shouldParseQuery() throws Exception {
////		RuntimeQueryRequest rQueryRequest = new RuntimeQueryRequest();
////		rQueryRequest.setPath("/");
////		rQueryRequest.setQuery("{\"name\":\"%name\", \"enabled\":\"%enabled\"}");
////
////		// Preparing Test DATA
////		// For String Values Test
////		RuntimeResourceParameter param1 = new RuntimeResourceParameter();
////		param1.setIn("query");
////		param1.setName("name");
////		param1.setType("String");
////		// For Non String Values Test
////		RuntimeResourceParameter param2 = new RuntimeResourceParameter();
////		param2.setIn("query");
////		param2.setName("enabled");
////		param2.setType("Boolean");
////		List<RuntimeResourceParameter> paramsList = new ArrayList<>();
////		paramsList.add(param1);
////		paramsList.add(param2);
////		rQueryRequest.setParameters(paramsList);
////		MockMultipartHttpServletRequest req = new MockMultipartHttpServletRequest();
////		req.setParameter("name", "codeonce");
////		req.setParameter("enabled", "true");
////		// Test Method that Should Parse Query And Replace Parameters
////		assertEquals("{\"name\":\"codeonce\", \"enabled\":true}",
////				queryUtils.parseQuery(rQueryRequest, rQueryRequest.getQuery(), null, null, req,null).get("query"));
////	}
//
//}
