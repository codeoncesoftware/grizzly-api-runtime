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
package fr.codeonce.grizzly.runtime.service.feign;

import fr.codeonce.grizzly.common.runtime.AWSLambdaRequest;
import fr.codeonce.grizzly.common.runtime.FunctionsExecuteRequest;
import fr.codeonce.grizzly.common.runtime.OpenFaasRequest;
import fr.codeonce.grizzly.common.runtime.Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "grizzly-api-runtime-function", url = "${grizzly-runtime-function-url}")
public interface FeignDiscoveryFunction {

    @PostMapping(value = "/function/execute", consumes = "application/json")
    public Response execute(FunctionsExecuteRequest f);

    @PostMapping(value = "/function/invoke", consumes = "application/json")
    public Response invoke(AWSLambdaRequest f) throws Exception;

    @PostMapping(value = "/function/openFaas", consumes = "application/json")
    public Response invokeOpenFaas(OpenFaasRequest f) throws Exception;
}