package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static com.bct.ngtpa.apiservice.architecture.SourceTextAssertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bct.ngtpa.apiservice.adapter.in.web.advice.ApiExceptionHandler;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorEventLogger;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorResponseRenderer;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMappingRegistry;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMappingRegistryConfig;
import com.bct.ngtpa.apiservice.adapter.in.web.error.PublicMessageResolver;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ServerWebExchange;

/**
 * Sub-task 1.8.3 (M1 closure, A1): both constructor call sites changed from (ErrorMessageResolver,
 * LoggingSanitizer, ExceptionMappingRegistry) to (ExceptionMappingRegistry, PublicMessageResolver,
 * ErrorEventLogger, ErrorResponseRenderer); {@code handleAccessDeniedException(...)} calls are now
 * {@code handle(...)}. No assertion changed.
 */
class ApiResponseExceptionHandlingBoundaryTest {

  /**
   * Sub-task 1.4 rewrite: this previously read {@code ApiExceptionHandler.java} source text for the
   * literal {@code "MutationResponse.<Void>failure"}, which moved to {@code ErrorResponseRenderer}
   * in sub-task 1.3. It now asserts the same boundary (no legacy {@code ApiErrorResponse} type
   * leaks out; every failure response is a {@code ResponseEntity<MutationResponse<Void>>}) by
   * calling a handler method directly and inspecting the real response.
   */
  @Test
  void apiExceptionHandlerDoesNotUseLegacyApiErrorResponse() {
    ApiExceptionHandler handler =
        new ApiExceptionHandler(
            testRegistry(),
            new PublicMessageResolver(testErrorMessageResolver()),
            new ErrorEventLogger(testLoggingSanitizer()),
            new ErrorResponseRenderer());
    ServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/test"));

    ResponseEntity<MutationResponse<Void>> response =
        handler.handle(new AccessDeniedException("denied"), exchange);

    assertNotNull(response.getBody());
    assertFalse(response.getBody().success());
    assertTrue(response.getBody() instanceof MutationResponse<Void>);
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
            "Mono<MutationResponse<UpdateNotificationsReadStatusResultResponse>> updateNotificationsReadStatus"));
    assertTrue(
        containsIgnoringWhitespace(
            notificationController,
            "Mono<MutationResponse<NotificationListResultResponse>> getNotifications"));

    String personalInformationController = readController("PersonalInformationController.java");
    assertTrue(
        containsIgnoringWhitespace(
            personalInformationController,
            "Mono<MutationResponse<PersonalInformationResultResponse>> getPersonalInformation"));
  }

  @Test
  void legacyFrontendBoundSuccessEndpointsRemainUnwrapped() throws IOException {
    String contributionController = readController("ContributionController.java");
    assertTrue(
        containsIgnoringWhitespace(
            contributionController, "Mono<ContributionListResponse> getContributionSummary"));
    assertTrue(
        containsIgnoringWhitespace(
            contributionController, "Mono<ResponseEntity<byte[]>> exportContributionSummary"));
  }

  /**
   * Sub-task 1.4 rewrite: this previously read {@code ApiExceptionHandler.java} source text for
   * {@code "requestLocale(exchange)"}. That helper still lives in {@code ApiExceptionHandler}
   * unchanged, but asserting behaviour instead of source text is more robust to future internal
   * refactors. Verifies the response uses the request's {@code Accept-Language} header rather than
   * a {@code lang} query parameter by observing the resolved message differs per header (both
   * requests target a pre-login-exempt path so the resolver actually runs).
   */
  @Test
  void exceptionLocalizationUsesAcceptLanguageNotLangQueryParameter() {
    ApiExceptionHandler handler =
        new ApiExceptionHandler(
            testRegistry(),
            new PublicMessageResolver(testErrorMessageResolver()),
            new ErrorEventLogger(testLoggingSanitizer()),
            new ErrorResponseRenderer());

    ServerWebExchange withLangQueryParamOnly =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/fund-price?lang=zh-HK"));
    ServerWebExchange withAcceptLanguageHeader =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/fund-price").header("Accept-Language", "zh-HK"));

    ResponseEntity<MutationResponse<Void>> ignoredQueryParam =
        handler.handle(new AccessDeniedException("denied"), withLangQueryParamOnly);
    ResponseEntity<MutationResponse<Void>> usedHeader =
        handler.handle(new AccessDeniedException("denied"), withAcceptLanguageHeader);

    // The lang= query param alone must not localize (resolver receives null locale); supplying
    // Accept-Language must localize to "zh-HK" being passed through to the message resolver.
    assertEquals("locale=null", ignoredQueryParam.getBody().errors().getFirst().message());
    assertEquals("locale=zh-HK", usedHeader.getBody().errors().getFirst().message());
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
    String path =
        switch (fileName) {
          case "NotificationController.java" ->
              "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/notification/NotificationController.java";
          case "PersonalInformationController.java" ->
              "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/personalinformation/PersonalInformationController.java";
          case "ContributionController.java" ->
              "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/contribution/ContributionController.java";
          default ->
              throw new IllegalArgumentException("Unsupported controller source: " + fileName);
        };
    return readSource(path);
  }

  private static String readSource(String path) throws IOException {
    return Files.readString(Path.of(path));
  }

  private static ErrorMessageResolver testErrorMessageResolver() {
    return (errorCode, locale, accountEnv, trustCode, schemeType) -> "locale=" + locale;
  }

  private static LoggingSanitizer testLoggingSanitizer() {
    MaskingProperties properties = new MaskingProperties();
    return new LoggingSanitizer(new ObjectMapper(), new MaskingService(properties));
  }

  private static ExceptionMappingRegistry testRegistry() {
    return new ExceptionMappingRegistryConfig().exceptionMappingRegistry();
  }
}
