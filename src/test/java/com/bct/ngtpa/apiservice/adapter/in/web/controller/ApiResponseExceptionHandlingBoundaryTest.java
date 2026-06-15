package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ApiResponseExceptionHandlingBoundaryTest {

    @Test
    void apiExceptionHandlerDoesNotUseLegacyApiErrorResponse() throws IOException {
        String source = readSource("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ApiExceptionHandler.java");

        assertFalse(source.contains("ApiErrorResponse"));
        assertFalse(source.contains("ResponseEntity<ApiErrorResponse>"));
        assertTrue(source.contains("ResponseEntity<MutationResponse<Void>>"));
        assertTrue(source.contains("MutationResponse.<Void>failure"));
    }

    @Test
    void controllersKeepConcreteResponseTypesInsteadOfExposingApiResponseDirectly() throws IOException {
        assertControllerDoesNotExposeApiResponseDirectly("NotificationController.java");
        assertControllerDoesNotExposeApiResponseDirectly("PersonalInformationController.java");
        assertControllerDoesNotExposeApiResponseDirectly("UpdatePersonalInformationController.java");
        assertControllerDoesNotExposeApiResponseDirectly("ContributionController.java");
    }

    @Test
    void mutationAndFormControllersKeepExpectedConcreteResponseTypes() throws IOException {
        String notificationController = readController("NotificationController.java");
        assertTrue(notificationController.contains(
                "Mono<MutationResponse<UpdateNotificationsReadStatusResponse>> updateNotificationsReadStatus"));

        String personalInformationController = readController("PersonalInformationController.java");
        assertTrue(personalInformationController.contains(
                "Mono<FormPageResponse<FormSchemaResponse>> getPersonalInformation"));

        String updatePersonalInformationController = readController("UpdatePersonalInformationController.java");
        assertTrue(updatePersonalInformationController.contains(
                "Mono<MutationResponse<PersonalInformationUpdateResultResponse>> update"));
    }

    @Test
    void legacyFrontendBoundSuccessEndpointsRemainUnwrapped() throws IOException {
        String notificationController = readController("NotificationController.java");
        assertTrue(notificationController.contains("Mono<NotificationListResponse> getNotifications"));

        String contributionController = readController("ContributionController.java");
        assertTrue(contributionController.contains("Mono<ContributionListResponse> getContributionSummary"));
        assertTrue(contributionController.contains("Mono<ResponseEntity<byte[]>> exportContributionSummary"));
    }

    @Test
    void exceptionLocalizationUsesAcceptLanguageNotLangQueryParameter() throws IOException {
        String source = readSource("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ApiExceptionHandler.java");

        assertFalse(source.contains("requestParam(exchange, \"lang\")"));
        assertTrue(source.contains("requestLocale(exchange)"));
    }

    private static void assertControllerDoesNotExposeApiResponseDirectly(String fileName) throws IOException {
        String source = readController(fileName);

        assertFalse(source.contains("Mono<ApiResponse>"), fileName + " must not expose Mono<ApiResponse>");
        assertFalse(source.contains("ResponseEntity<ApiResponse>"), fileName + " must not expose ResponseEntity<ApiResponse>");
        assertFalse(source.contains(" ApiResponse "), fileName + " must not expose ApiResponse directly");
    }

    private static String readController(String fileName) throws IOException {
        return readSource("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/" + fileName);
    }

    private static String readSource(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}

