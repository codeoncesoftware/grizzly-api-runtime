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
package fr.codeonce.grizzly.runtime.util;



import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.NoSuchElementException;

/**
 * Handles / Catches all APP exceptions and returns the suitable message
 */
@RestControllerAdvice
public class GlobalExceptionHandler {


    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseBody
    public ResponseEntity<String> handleIllegalException(DuplicateKeyException e) {
        logException(e);
        String message = e.getMessage().replaceAll("\"", "\\\\\"");
        String jsonResponse = "{\"message\": \"" + message + "\"}";

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonResponse);
    }


    @ResponseStatus(HttpStatus.NOT_ACCEPTABLE)
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public String handleIllegalException(IllegalArgumentException e) {
        logException(e);
        return ExceptionUtils.getRootCause(e).getMessage();
    }


    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(NoSuchElementException.class)
    @ResponseBody
    public String handleIllegalException(NoSuchElementException e) {
        logException(e);
        return ExceptionUtils.getRootCause(e).getMessage();
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public String handleIllegalException(Exception e) {
        logException(e);
        return ExceptionUtils.getRootCause(e).getMessage();
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(IllegalStateException.class)
    @ResponseBody
    public String handleHttpClientErrorException(IllegalStateException e) {
        logException(e);
        return ExceptionUtils.getRootCause(e).getMessage();
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(BadCredentialsException.class)
    @ResponseBody
    public String handleHttpClientErrorException(BadCredentialsException e) {
        logException(e);
        return ExceptionUtils.getRootCause(e).getMessage();
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(CustomGitAPIException.class)
    @ResponseBody
    public String handleUnauthorizedGitClone(CustomGitAPIException e) {
        logException(e);
        return ExceptionUtils.getRootCause(e).getMessage();
    }

    private void logException(Exception e) {
        log.error("an error occurred", e);
    }

}