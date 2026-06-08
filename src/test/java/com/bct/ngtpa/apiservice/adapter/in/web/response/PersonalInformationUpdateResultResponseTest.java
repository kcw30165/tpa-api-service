package com.bct.ngtpa.apiservice.adapter.in.web.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

public class PersonalInformationUpdateResultResponseTest {
        @Test
    void updatePersonalInformationResponseConstructorAndFactoriesCoverMessagesAndErrorsBranches() {
        PersonalInformationUpdateResultResponse result = new PersonalInformationUpdateResultResponse(
                "REF-1", "08/06/2026", "09:17:00");
        UpdatePersonalInformationResponse defaulted = new UpdatePersonalInformationResponse(true, "UPDATED", result, null, null);
        assertTrue(defaulted.messages().isEmpty());
        assertTrue(defaulted.errors().isEmpty());

        UpdatePersonalInformationResponse updated = UpdatePersonalInformationResponse.updated(result);
        assertTrue(updated.success());
        assertEquals(ApiStatus.UPDATED.toString(), updated.status());
        assertNotNull(updated.result());
        assertEquals(1, updated.messages().size());
        assertTrue(updated.errors().isEmpty());

        ApiError validationError = ApiError.field("email.required", "Email required", List.of("emailAddress"), "SERVER");
        UpdatePersonalInformationResponse validationFailed = UpdatePersonalInformationResponse.validationFailed(List.of(validationError));
        assertFalse(validationFailed.success());
        assertEquals(ApiStatus.VALIDATION_FAILED.toString(), validationFailed.status());
        assertEquals(List.of(validationError), validationFailed.errors());

        ApiError businessError = ApiError.business("business.rejected", "Rejected", List.of(), "SERVER");
        UpdatePersonalInformationResponse businessRejected = UpdatePersonalInformationResponse.businessRejected(List.of(businessError));
        assertFalse(businessRejected.success());
        assertEquals(ApiStatus.BUSINESS_REJECTED.toString(), businessRejected.status());
        assertEquals(List.of(businessError), businessRejected.errors());

        ApiError systemError = ApiError.system("system.error", "System error", "SERVER");
        UpdatePersonalInformationResponse systemErrorResponse = UpdatePersonalInformationResponse.systemError(List.of(systemError));
        assertFalse(systemErrorResponse.success());
        assertEquals(ApiStatus.SYSTEM_ERROR.toString(), systemErrorResponse.status());
        assertEquals(List.of(systemError), systemErrorResponse.errors());
    }
}
