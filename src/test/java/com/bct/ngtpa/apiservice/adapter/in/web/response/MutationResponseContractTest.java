package com.bct.ngtpa.apiservice.adapter.in.web.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MutationResponseContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void successFactoryUsesStandardMutationEnvelope() {
        var message = ApiMessage.success("mutation.updated", "Updated", "PAGE");

        var response = MutationResponse.success(ApiStatus.UPDATED, "payload", List.of(message));

        assertTrue(response.success());
        assertEquals(ApiStatus.UPDATED, response.status());
        assertEquals("payload", response.result());
        assertEquals(List.of(message), response.messages());
        assertTrue(response.errors().isEmpty());
    }

    @Test
    void failureFactoryPutsBlockingErrorsInErrorsArrayAndLeavesMessagesEmpty() {
        var error = ApiError.field("field.required", "Field is required", List.of("emailAddress"), "BFF");

        var response = MutationResponse.failure(ApiStatus.VALIDATION_FAILED, List.of(error));

        assertFalse(response.success());
        assertEquals(ApiStatus.VALIDATION_FAILED, response.status());
        assertNull(response.result());
        assertTrue(response.messages().isEmpty());
        assertEquals(List.of(error), response.errors());
    }

    @Test
    void constructorNormalizesNullCollectionsToEmptyArrays() {
        var response = new MutationResponse<>(true, ApiStatus.NO_CHANGE, null, null, null);

        assertNotNull(response.messages());
        assertNotNull(response.errors());
        assertTrue(response.messages().isEmpty());
        assertTrue(response.errors().isEmpty());
    }

    @Test
    void constructorDefensivelyCopiesMessagesAndErrors() {
        var messages = new ArrayList<>(List.of(ApiMessage.info("info", "Info", "PAGE")));
        var errors = new ArrayList<>(List.of(ApiError.business("business", "Business", List.of(), "BFF")));

        var response = new MutationResponse<>(false, ApiStatus.BUSINESS_REJECTED, null, messages, errors);
        messages.clear();
        errors.clear();

        assertEquals(1, response.messages().size());
        assertEquals(1, response.errors().size());
        assertThrows(UnsupportedOperationException.class, () -> response.messages().add(ApiMessage.info("x", "x", "PAGE")));
        assertThrows(UnsupportedOperationException.class, () -> response.errors().add(ApiError.system("x", "x", "BFF")));
    }

    @Test
    void serializesSuccessUsingStandardFieldNamesAndArrayCollections() throws Exception {
        var response = MutationResponse.success(ApiStatus.UPDATED, "payload", List.of());

        JsonNode json = objectMapper.valueToTree(response);

        assertTrue(json.get("success").asBoolean());
        assertEquals("UPDATED", json.get("status").asText());
        assertEquals("payload", json.get("result").asText());
        assertTrue(json.get("messages").isArray());
        assertEquals(0, json.get("messages").size());
        assertTrue(json.get("errors").isArray());
        assertEquals(0, json.get("errors").size());
        assertFalse(json.has("errorCode"));
        assertFalse(json.has("message"));
        assertFalse(json.has("requestId"));
    }

    @Test
    void serializesFailureWithBlockingErrorsInErrorsArray() throws Exception {
        var error = ApiError.field("email.required", "Email is required", List.of("emailAddress"), "BFF");
        var response = MutationResponse.failure(ApiStatus.VALIDATION_FAILED, List.of(error));

        JsonNode json = objectMapper.valueToTree(response);

        assertFalse(json.get("success").asBoolean());
        assertEquals("VALIDATION_FAILED", json.get("status").asText());
        assertTrue(json.get("result").isNull());
        assertTrue(json.get("messages").isArray());
        assertEquals(0, json.get("messages").size());
        assertTrue(json.get("errors").isArray());
        assertEquals(1, json.get("errors").size());
        assertEquals("FIELD", json.get("errors").get(0).get("type").asText());
        assertEquals("email.required", json.get("errors").get(0).get("code").asText());
        assertEquals("Email is required", json.get("errors").get(0).get("message").asText());
        assertEquals("emailAddress", json.get("errors").get(0).get("targets").get(0).asText());
        assertFalse(json.has("errorCode"));
        assertFalse(json.has("requestId"));
    }
}
