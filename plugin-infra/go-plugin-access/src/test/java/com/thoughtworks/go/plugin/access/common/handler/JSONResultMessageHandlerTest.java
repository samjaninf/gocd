/*
 * Copyright Thoughtworks, Inc.
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
package com.thoughtworks.go.plugin.access.common.handler;

import com.thoughtworks.go.plugin.api.response.Result;
import com.thoughtworks.go.plugin.api.response.validation.ValidationError;
import com.thoughtworks.go.plugin.api.response.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

public class JSONResultMessageHandlerTest {
    private JSONResultMessageHandler messageHandler;

    @BeforeEach
    public void setUp() {
        messageHandler = new JSONResultMessageHandler();
    }

    @Test
    public void shouldBuildValidationResultFromResponseBody() {
        String responseBody = "[{\"key\":\"key-one\",\"message\":\"incorrect value\"},{\"message\":\"general error\"}]";
        ValidationResult validationResult = messageHandler.toValidationResult(responseBody);
        assertValidationError(validationResult.getErrors().get(0), "key-one", "incorrect value");
        assertValidationError(validationResult.getErrors().get(1), "", "general error");
    }

    @Test
    public void shouldBuildSuccessResultFromResponseBody() {
        String responseBody = "{\"status\":\"success\",messages=[\"message-one\",\"message-two\"]}";
        Result result = messageHandler.toResult(responseBody);
        assertSuccessResult(result, List.of("message-one", "message-two"));
    }

    @Test
    public void shouldBuildFailureResultFromResponseBody() {
        String responseBody = "{\"status\":\"failure\",messages=[\"message-one\",\"message-two\"]}";
        Result result = messageHandler.toResult(responseBody);
        assertFailureResult(result, List.of("message-one", "message-two"));
    }

    @Test
    public void shouldValidateIncorrectJsonForValidationResult() {
        assertThat(errorMessageForValidationResult("{{\"key\":\"abc\",\"message\":\"msg\"}}")).isEqualTo("Unable to de-serialize json response. Validation errors should be returned as list of errors, with each error represented as a map");
        assertThat(errorMessageForValidationResult("[[{\"key\":\"abc\",\"message\":\"msg\"}]]")).isEqualTo("Unable to de-serialize json response. Validation errors should be returned as list of errors, with each error represented as a map");
        assertThat(errorMessageForValidationResult("[{\"key\":true,\"message\":\"msg\"}]")).isEqualTo("Unable to de-serialize json response. Validation error key should be of type string");
        assertThat(errorMessageForValidationResult("[{\"key\":\"abc\",\"message\":{}}]")).isEqualTo("Unable to de-serialize json response. Validation message should be of type string");
        assertThat(errorMessageForValidationResult("[{\"key\":\"abc\",\"message\":[]}]")).isEqualTo("Unable to de-serialize json response. Validation message should be of type string");
    }

    @Test
    public void shouldValidateIncorrectJsonForCheckConnectionResult() {
        assertThat(errorMessageForCheckConnectionResult("")).isEqualTo("Unable to de-serialize json response. Empty response body");
        assertThat(errorMessageForCheckConnectionResult("[{\"result\":\"success\"}]")).isEqualTo("Unable to de-serialize json response. Check connection result should be returned as map, with status represented as string and messages represented as list");
        assertThat(errorMessageForCheckConnectionResult("{\"status\":true}")).isEqualTo("Unable to de-serialize json response. Check connection 'status' should be of type string");
        assertThat(errorMessageForCheckConnectionResult("{\"result\":true}")).isEqualTo("Unable to de-serialize json response. Check connection 'status' is a required field");
        assertThat(errorMessageForCheckConnectionResult("{\"status\":\"success\",\"messages\":[{},{}]}")).isEqualTo("Unable to de-serialize json response. Check connection 'message' should be of type string");
    }

    private void assertValidationError(ValidationError validationError, String expectedKey, String expectedMessage) {
        assertThat(validationError.getKey()).isEqualTo(expectedKey);
        assertThat(validationError.getMessage()).isEqualTo(expectedMessage);
    }

    private void assertSuccessResult(Result result, List<String> messages) {
        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getMessages()).isEqualTo(messages);
    }

    private void assertFailureResult(Result result, List<String> messages) {
        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getMessages()).isEqualTo(messages);
    }

    private String errorMessageForValidationResult(String message) {
        try {
            messageHandler.toValidationResult(message);
            fail("should have thrown exception");
        } catch (Exception e) {
            return e.getMessage();
        }
        return null;
    }

    private String errorMessageForCheckConnectionResult(String message) {
        try {
            messageHandler.toResult(message);
            fail("should have thrown exception");
        } catch (Exception e) {
            return e.getMessage();
        }
        return null;
    }
}
