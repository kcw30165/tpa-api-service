package com.bct.ngtpa.apiservice.adapter.in.web.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GenericResponseTest {

    @Test
    void apiErrorConstructorDefaultsTargetsAndSeverityAndPreservesCustomSeverity() {
        ApiError defaulted = new ApiError("FIELD", "code", "message", null, null, "SERVER");
        assertEquals(List.of(), defaulted.targets());
        assertEquals("ERROR", defaulted.severity());

        ApiError blankSeverity = new ApiError("FIELD", "code", "message", List.of("emailAddress"), "   ", "SERVER");
        assertEquals(List.of("emailAddress"), blankSeverity.targets());
        assertEquals("ERROR", blankSeverity.severity());

        ApiError warning = new ApiError("FIELD", "code", "message", List.of("emailAddress"), "WARNING", "SERVER");
        assertEquals("WARNING", warning.severity());
    }

    @Test
    void apiErrorFactoryMethodsCreateExpectedErrorTypes() {
        assertEquals("FIELD", ApiError.field("field.code", "Field message", List.of("email"), "SERVER").type());
        assertEquals("CROSS_FIELD",
                ApiError.crossField("cross.code", "Cross message", List.of("a", "b"), "SERVER").type());
        assertEquals("FORM", ApiError.form("form.code", "Form message", List.of(), "SERVER").type());
        assertEquals("BUSINESS", ApiError.business("business.code", "Business message", List.of(), "SERVER").type());

        ApiError system = ApiError.system("system.code", "System message", "SERVER");
        assertEquals("SYSTEM", system.type());
        assertEquals(List.of(), system.targets());
    }

    @Test
    void apiMessageFactoryMethodsCreateExpectedMessageTypes() {
        assertEquals("SUCCESS", ApiMessage.success("success.code", "Saved", "PAGE").type());
        assertEquals("INFO", ApiMessage.info("info.code", "Information", "PAGE").type());
        assertEquals("WARNING", ApiMessage.warning("warning.code", "Warning", "PAGE").type());
    }

    @Test
    void mutationResponseConstructorDefaultsNullCollectionsAndFactoriesPopulateExpectedBranches() {
        MutationResponse<String> defaulted = new MutationResponse<>(true, ApiStatus.SUCCESS, "ok", null, null);
        assertTrue(defaulted.messages().isEmpty());
        assertTrue(defaulted.errors().isEmpty());

        ApiMessage message = ApiMessage.info("info.code", "Info", "PAGE");
        MutationResponse<String> success = MutationResponse.success(ApiStatus.UPDATED, "updated", List.of(message));
        assertTrue(success.success());
        assertEquals(ApiStatus.UPDATED, success.status());
        assertEquals(List.of(message), success.messages());
        assertTrue(success.errors().isEmpty());

        ApiError error = ApiError.business("business.code", "Rejected", List.of(), "SERVER");
        MutationResponse<String> failure = MutationResponse.failure(ApiStatus.BUSINESS_REJECTED, List.of(error));
        assertFalse(failure.success());
        assertNull(failure.result());
        assertTrue(failure.messages().isEmpty());
        assertEquals(List.of(error), failure.errors());
    }

    @Test
    void formPageResponseConstructorDefaultsNullCollectionsAndSuccessFactoryUsesEmptyCollections() {
        PageResponse page = new PageResponse("personalInformationPage", "Personal Information", "en");
        FormSchemaResponse form = new FormSchemaResponse("form", "1.0", "view", null, null, null, null);

        FormPageResponse<FormSchemaResponse> defaulted = new FormPageResponse<>(true, ApiStatus.SUCCESS, page, form,
                null, null);
        assertTrue(defaulted.messages().isEmpty());
        assertTrue(defaulted.errors().isEmpty());

        FormPageResponse<FormSchemaResponse> response = FormPageResponse.success(page, form);
        assertTrue(response.success());
        assertEquals(ApiStatus.SUCCESS, response.status());
        assertEquals(page, response.page());
        assertEquals(form, response.form());
        assertTrue(response.messages().isEmpty());
        assertTrue(response.errors().isEmpty());
    }

    @Test
    void sectionAndNotificationStatusResponsesDefaultNullListsAndCopyNonNullLists() {
        SectionResponse emptySection = new SectionResponse("address", "Address", 1, null);
        assertTrue(emptySection.fields().isEmpty());

        FieldResponse field = fieldResponse("emailAddress");
        SectionResponse populatedSection = new SectionResponse("contact", "Contact", 2, List.of(field));
        assertEquals(List.of(field), populatedSection.fields());

        UpdateNotificationsReadStatusResponse emptyNotifications = new UpdateNotificationsReadStatusResponse(null);
        assertTrue(emptyNotifications.notifications().isEmpty());

        NotificationReadStatusDto notification = new NotificationReadStatusDto("MSG-1", true);
        UpdateNotificationsReadStatusResponse populatedNotifications = new UpdateNotificationsReadStatusResponse(
                List.of(notification));
        assertEquals(List.of(notification), populatedNotifications.notifications());
    }

    @Test
    void fieldResponseNormalizesNullEmptyAndPopulatedOptionalCollections() {
        FieldResponse nullCollections = new FieldResponse(
                "emailAddress", "Email", "string", "email", null, null,
                false, true, null, 120, null, null, null,
                null, 10, null);
        assertNull(nullCollections.copyWhenChecked());
        assertNull(nullCollections.validations());

        FieldResponse emptyCollections = new FieldResponse(
                "emailAddress", "Email", "string", "email", null, null,
                false, true, null, 120, null, null, null,
                Map.of(), 10, List.of());
        assertNull(emptyCollections.copyWhenChecked());
        assertNull(emptyCollections.validations());

        ValidationRuleResponse validation = new ValidationRuleResponse(
                "email.required", "required", null, null, null, "error", "email.required", "Email is required");
        Map<String, Object> copyWhenChecked = Map.of("fromTo", Map.of("a", "b"));
        FieldResponse populated = new FieldResponse(
                "sameAsResidential", "Same as residential", "boolean", "checkbox", true, false,
                false, false, null, null, null, null, null,
                copyWhenChecked, 20, List.of(validation));
        assertEquals(copyWhenChecked, populated.copyWhenChecked());
        assertEquals(List.of(validation), populated.validations());
    }

    @Test
    void validationRuleResponseNormalizesWhenThenAndFromHandlesEmptyAndPartialMaps() {
        ValidationRuleResponse nullMaps = new ValidationRuleResponse(
                "rule", "conditionalRequired", null, null, null, "error", "code", "message");
        assertNull(nullMaps.when());
        assertNull(nullMaps.then());

        ValidationRuleResponse emptyMaps = new ValidationRuleResponse(
                "rule", "conditionalRequired", null, Map.of(), Map.of(), "error", "code", "message");
        assertNull(emptyMaps.when());
        assertNull(emptyMaps.then());

        Map<String, Object> when = Map.of("operator", "notBlank", "field", "emailAddress");
        Map<String, Object> then = Map.of("operator", "required", "field", "emailAddress");
        ValidationRuleResponse populated = new ValidationRuleResponse(
                "rule", "conditionalRequired", 1, when, then, "error", "code", "message");
        assertEquals(when, populated.when());
        assertEquals(then, populated.then());

        assertNull(ValidationRuleResponse.from(null));
        assertNull(ValidationRuleResponse.from(Map.of()));

        Map<String, Object> invalidNestedMaps = new LinkedHashMap<>();
        invalidNestedMaps.put("id", 123);
        invalidNestedMaps.put("when", "not-a-map");
        invalidNestedMaps.put("then", "not-a-map");
        ValidationRuleResponse invalidNested = ValidationRuleResponse.from(invalidNestedMaps);
        assertEquals("123", invalidNested.id());
        assertNull(invalidNested.when());
        assertNull(invalidNested.then());

        Map<String, Object> fullSource = new LinkedHashMap<>();
        fullSource.put("id", "rule-1");
        fullSource.put("type", "conditionalRequired");
        fullSource.put("value", 5);
        fullSource.put("when", when);
        fullSource.put("then", then);
        fullSource.put("severity", "error");
        fullSource.put("code", "rule.code");
        fullSource.put("message", "Rule message");
        ValidationRuleResponse fromFullSource = ValidationRuleResponse.from(fullSource);
        assertEquals("rule-1", fromFullSource.id());
        assertEquals(5, fromFullSource.value());
        assertEquals(when, fromFullSource.when());
        assertEquals(then, fromFullSource.then());
    }

    @Test
    void formSchemaResponseDefaultsNullCollectionsAndCopiesPopulatedCollections() {
        FormSchemaResponse defaulted = new FormSchemaResponse("form", "1.0", "view", null, null, null, null);
        assertTrue(defaulted.sections().isEmpty());
        assertTrue(defaulted.validationRules().isEmpty());
        assertTrue(defaulted.confirmation().isEmpty());
        assertTrue(defaulted.actions().isEmpty());

        SectionResponse section = new SectionResponse("section", "Section", 1, List.of(fieldResponse("field")));
        ValidationRuleResponse validation = new ValidationRuleResponse("rule", "required", null, null, null, "error",
                "code", "message");
        Map<String, Object> confirmation = Map.of("enabled", true);
        Map<String, Object> actions = Map.of("submit", Map.of("enabled", true));
        FormSchemaResponse populated = new FormSchemaResponse(
                "form", "1.0", "edit", List.of(section), List.of(validation), confirmation, actions);

        assertEquals(List.of(section), populated.sections());
        assertEquals(List.of(validation), populated.validationRules());
        assertEquals(confirmation, populated.confirmation());
        assertEquals(actions, populated.actions());
    }

    @Test
    void apiStatusContainsExpectedEnvelopeStatuses() {
        assertEquals(ApiStatus.SUCCESS, ApiStatus.valueOf("SUCCESS"));
        assertEquals(ApiStatus.UPDATED, ApiStatus.valueOf("UPDATED"));
        assertEquals(ApiStatus.SUBMITTED, ApiStatus.valueOf("SUBMITTED"));
        assertEquals(ApiStatus.NO_CHANGE, ApiStatus.valueOf("NO_CHANGE"));
        assertEquals(ApiStatus.PENDING_APPROVAL, ApiStatus.valueOf("PENDING_APPROVAL"));
        assertEquals(ApiStatus.VALIDATION_FAILED, ApiStatus.valueOf("VALIDATION_FAILED"));
        assertEquals(ApiStatus.BUSINESS_REJECTED, ApiStatus.valueOf("BUSINESS_REJECTED"));
        assertEquals(ApiStatus.SYSTEM_ERROR, ApiStatus.valueOf("SYSTEM_ERROR"));
    }

    private static FieldResponse fieldResponse(String name) {
        return new FieldResponse(
                name, "Label", "string", "text", "value", "original",
                false, false, null, 40, null, "Placeholder", null,
                null, 1, null);
    }
}
