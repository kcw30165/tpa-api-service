package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static com.bct.ngtpa.apiservice.architecture.SourceTextAssertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ApiResponseExceptionHandlingBoundaryTest {

  @Test
  void apiExceptionHandlerDoesNotUseLegacyApiErrorResponse() throws IOException {
    String source =
        readSource(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ApiExceptionHandler.java");

    assertFalse(containsIgnoringWhitespace(source, "ApiErrorResponse"));
    assertFalse(containsIgnoringWhitespace(source, "ResponseEntity<ApiErrorResponse>"));
    assertTrue(containsIgnoringWhitespace(source, "ResponseEntity<MutationResponse<Void>>"));
    assertTrue(containsIgnoringWhitespace(source, "MutationResponse.<Void>failure"));
  }

  @Test
  void controllersKeepConcreteResponseTypesInsteadOfExposingApiResponseDirectly()
      throws IOException {
    assertControllerDoesNotExposeApiResponseDirectly("NotificationController.java");
    assertControllerDoesNotExposeApiResponseDirectly("PersonalInformationController.java");
    assertControllerDoesNotExposeApiResponseDirectly("ContributionController.java");
  }

  @Test
  void mutationAndFormControllersKeepExpectedConcreteResponseTypes() throws IOException {
    String notificationController = readController("NotificationController.java");
    assertTrue(
        containsIgnoringWhitespace(
            notificationController,
            "Mono<MutationResponse<UpdateNotificationsReadStatusResponse>> updateNotificationsReadStatus"));

    String personalInformationController = readController("PersonalInformationController.java");
    assertTrue(
        containsIgnoringWhitespace(
            personalInformationController,
            "Mono<FormPageResponse<FormSchemaResponse>> getPersonalInformation"));
  }

  @Test
  void legacyFrontendBoundSuccessEndpointsRemainUnwrapped() throws IOException {
    String notificationController = readController("NotificationController.java");
    assertTrue(
        containsIgnoringWhitespace(
            notificationController, "Mono<NotificationListResponse> getNotifications"));

    String contributionController = readController("ContributionController.java");
    assertTrue(
        containsIgnoringWhitespace(
            contributionController, "Mono<ContributionListResponse> getContributionSummary"));
    assertTrue(
        containsIgnoringWhitespace(
            contributionController, "Mono<ResponseEntity<byte[]>> exportContributionSummary"));
  }

  @Test
  void exceptionLocalizationUsesAcceptLanguageNotLangQueryParameter() throws IOException {
    String source =
        readSource(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ApiExceptionHandler.java");

    assertFalse(containsIgnoringWhitespace(source, "requestParam(exchange, \"lang\")"));
    assertTrue(containsIgnoringWhitespace(source, "requestLocale(exchange)"));
  }

  private static void assertControllerDoesNotExposeApiResponseDirectly(String fileName)
      throws IOException {
    String source = readController(fileName);

    assertFalse(
        containsIgnoringWhitespace(source, "Mono<ApiResponse>"),
        fileName + " must not expose Mono<ApiResponse>");
    assertFalse(
        containsIgnoringWhitespace(source, "ResponseEntity<ApiResponse>"),
        fileName + " must not expose ResponseEntity<ApiResponse>");
    assertFalse(
        containsIgnoringWhitespace(source, " ApiResponse "),
        fileName + " must not expose ApiResponse directly");
  }

  private static String readController(String fileName) throws IOException {
    return readSource(
        "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/" + fileName);
  }

  private static String readSource(String path) throws IOException {
    return Files.readString(Path.of(path));
  }
}
