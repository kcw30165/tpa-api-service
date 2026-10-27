package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.advice.ApiExceptionHandler;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorEventLogger;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorResponseRenderer;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMappingRegistryConfig;
import com.bct.ngtpa.apiservice.adapter.in.web.error.PublicMessageResolver;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateMemberInfoApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.UpdateMemberInfoCommand;
import com.bct.ngtpa.apiservice.application.service.account.AccountIdentifierFormatter;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PersonalInformationUpdateOutcomeAdapterTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  @ParameterizedTest
  @MethodSource("invalidResponses")
  void invalidResponseIsPutSpecificServiceUnavailable(String response) throws Exception {
    var executor = mock(ApimExchangeExecutor.class);
    when(executor.postEnvelope(any())).thenAnswer(ignored -> Mono.just(envelope(response)));

    assertUnavailable(adapter(executor).updateMemberInfo(command(true)));
    verify(executor, times(1)).postEnvelope(any());
  }

  static Stream<String> invalidResponses() {
    return Stream.of(
        "{}",
        "{\"response\":{\"err-message\":\"\"}}",
        "{\"response\":{\"data\":[]}}",
        "{\"response\":{\"err-message\":\"private downstream text\"}}",
        "{\"response\":{\"err-message\":\" \"}}",
        response("[]"),
        response("[null]"),
        response("[{\"success\":true,\"policy-no\":\"118\",\"cert-no\":\"4\",\"env\":\"DB\"}]"),
        response("[" + row("3", true, "[]") + "," + row("3", true, "[]") + "]"),
        response("[" + row("3", false, "[]") + "]"),
        response("[" + row("3", false, "null") + "]"),
        response("[" + row("3", false, "[null]") + "]"),
        response("[{\"success\":true,\"policy-no\":null,\"cert-no\":\"3\",\"env\":\"DB\"}]"),
        response("[{\"success\":true,\"policy-no\":\" \",\"cert-no\":\"3\",\"env\":\"DB\"}]"),
        response("[{\"success\":true,\"policy-no\":\"abc\",\"cert-no\":\"3\",\"env\":\"DB\"}]"),
        response("[{\"success\":true,\"policy-no\":\"0\",\"cert-no\":\"3\",\"env\":\"DB\"}]"),
        response("[{\"success\":true,\"policy-no\":\"123456789012\",\"cert-no\":\"3\",\"env\":\"DB\"}]"),
        response("[{\"success\":true,\"policy-no\":\"118\",\"cert-no\":null,\"env\":\"DB\"}]"),
        response("[" + row("0", true, "[]") + "]"),
        response("[" + row("-1", true, "[]") + "]"),
        response("[" + row("10000", true, "[]") + "]"),
        response("[{\"success\":true,\"policy-no\":\"118\",\"cert-no\":\"3\",\"env\":\" \"}]"));
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"abc", "", " ", "0000", "3.5", "2147483648"})
  void invalidDecryptedCertificateStringIsUnavailable(String certificate) {
    var executor = mock(ApimExchangeExecutor.class);
    when(executor.postEnvelope(any()))
        .thenAnswer(
            ignored -> Mono.just(envelope(response(
                "[" + row(certificate, true, "[]") + "]"))));

    assertUnavailable(adapter(executor).updateMemberInfo(command(true)));
  }

  @Test
  void responseCertificateRemainsAPaddedString() throws Exception {
    assertThat(envelope(response("[" + row("0003", true, "[]") + "]"))
        .getResponse().getData().getFirst().getCertNo()).isEqualTo("0003");
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"3", "0003", " 0003 "})
  void validatesTrimmedDecryptedStringsWithoutLosingOriginalCertificate(String certificate) {
    var executor = mock(ApimExchangeExecutor.class);
    when(executor.postEnvelope(any()))
        .thenAnswer(ignored -> Mono.just(envelope(response(
            "[" + row(certificate, true, "[]") + "]"))));

    StepVerifier.create(adapter(executor).updateMemberInfo(command(true)))
        .assertNext(
            rows -> {
              assertThat(rows).hasSize(1);
              assertThat(rows.getFirst().selected()).isTrue();
              assertThat(rows.getFirst().certNo()).isEqualTo(certificate);
            })
        .verifyComplete();
  }

  @Test
  void logsProtocolFailuresAndDroppedOtherAccountsWithoutIdentifiersOrRawErrors() throws Exception {
    var logger =
        (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(ApimUpdatePersonalInformationAdapter.class);
    var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      var executor = mock(ApimExchangeExecutor.class);
      when(executor.postEnvelope(any()))
          .thenAnswer(ignored -> Mono.just(envelope(response("[" + row("3", true, "[]") + "," + row("4", true, "[]") + "]"))));
      adapter(executor).updateMemberInfo(command(false)).block();
      when(executor.postEnvelope(any()))
          .thenAnswer(ignored -> Mono.just(envelope("{\"response\":{\"err-message\":\"private detail\"}}")));
      assertUnavailable(adapter(executor).updateMemberInfo(command(true)));

      var messages = appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).toList();
      assertThat(messages).anyMatch(message -> message.contains("other-accounts-dropped count=1"));
      assertThat(messages).anyMatch(message -> message.contains("unavailable reason=INVALID_ENVELOPE"));
      assertThat(String.join("\n", messages)).doesNotContain("00000000118", "0003", "private detail", "a@b.test");
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @ParameterizedTest
  @MethodSource("transportFailures")
  void exhaustedTransportFailureIsPutSpecificServiceUnavailable(Throwable failure) {
    var executor = mock(ApimExchangeExecutor.class);
    when(executor.postEnvelope(any())).thenReturn(Mono.error(failure));

    assertUnavailable(adapter(executor).updateMemberInfo(command(true)));
    verify(executor, times(1)).postEnvelope(any());
  }

  static Stream<Throwable> transportFailures() {
    return Stream.of(
        new TimeoutException("private timeout"),
        new IOException("private IO failure"),
        new WebClientRequestException(
            new IOException("private connection"),
            HttpMethod.POST,
            URI.create("https://example.test"),
            HttpHeaders.EMPTY),
        new com.bct.ngtpa.apiservice.exception.ApimException(
            HttpStatus.BAD_GATEWAY, "private downstream detail"),
        Exceptions.retryExhausted("retry exhausted", new TimeoutException("private timeout")));
  }

  @Test
  void stringCertificateMatchesLeadingZerosAndOtherRowsAreDroppedWhenApplyAllIsFalse()
      throws Exception {
    var executor = mock(ApimExchangeExecutor.class);
    when(executor.postEnvelope(any()))
        .thenAnswer(ignored -> Mono.just(envelope(response("[" + row("3", true, "[]") + "," + row("4", false, "[]") + "]"))));

    StepVerifier.create(adapter(executor).updateMemberInfo(command(false)))
        .assertNext(
            rows -> {
              assertThat(rows).hasSize(1);
              assertThat(rows.getFirst().selected()).isTrue();
              assertThat(rows.getFirst().certNo()).isEqualTo("3");
            })
        .verifyComplete();
  }

  @Test
  void emptyExecutorResultIsUnavailable() {
    var executor = mock(ApimExchangeExecutor.class);
    when(executor.postEnvelope(any())).thenReturn(Mono.empty());

    assertUnavailable(adapter(executor).updateMemberInfo(command(true)));
  }

  @Test
  void programmingErrorsAreNotRelabeledAsTransportFailures() {
    var executor = mock(ApimExchangeExecutor.class);
    var failure = new IllegalStateException("programming error");
    when(executor.postEnvelope(any())).thenReturn(Mono.error(failure));

    StepVerifier.create(adapter(executor).updateMemberInfo(command(true)))
        .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
        .verify();
  }

  @Test
  void pureClassifierExistsWithoutSpringOrAdapterDependencies() throws Exception {
    var source =
        java.nio.file.Files.readString(
            java.nio.file.Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/service/personalinformation/PersonalInformationUpdateOutcomeClassifier.java"));
    assertThat(source).doesNotContain("org.springframework", "adapter.", "lombok.extern.slf4j");
  }

  @Test
  void genericApimEnvelopeRejectionStillUsesItsExistingStatus() {
    assertThatThrownBy(
            () -> ApimResponseValidator.requireSuccessData(envelope("{\"response\":{\"err-message\":\"rejected\"}}")))
        .isInstanceOfSatisfying(
            com.bct.ngtpa.apiservice.exception.ApimException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
  }

  private static void assertUnavailable(Mono<?> operation) {
    StepVerifier.create(operation)
        .expectErrorSatisfies(
            error -> {
              var handler =
                  new ApiExceptionHandler(
                      new ExceptionMappingRegistryConfig().exceptionMappingRegistry(),
                      new PublicMessageResolver((code, locale, accountEnv, trust, scheme) -> "resolved"),
                      new ErrorEventLogger(
                          new LoggingSanitizer(JSON, new MaskingService(new MaskingProperties()))),
                      new ErrorResponseRenderer());
              var exchange =
                  MockServerWebExchange.from(
                      MockServerHttpRequest.put("/api/v1/personal-information").build());
              var response =
                  handler.handle(org.junit.jupiter.api.Assertions.assertInstanceOf(Exception.class, error), exchange);
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
              var body = response.getBody();
              assertThat(body.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
              assertThat(body.success()).isFalse();
              assertThat(body.result()).isNull();
              assertThat(body.messages()).isEmpty();
              assertThat(body.errors()).singleElement().satisfies(
                  entry -> {
                    assertThat(entry.code()).isEqualTo("err.personal-information.update.unavailable");
                    assertThat(entry.message()).isNull();
                    assertThat(entry.targets()).isEmpty();
                  });
            })
        .verify();
  }

  private static UpdateMemberInfoCommand command(boolean applyAll) {
    return new UpdateMemberInfoCommand(
        "DB", "00000000118", "0003", "user", ActorUserRole.ROLE_USER_MBR, applyAll, Map.of("email", "a@b.test"));
  }

  private static ApimUpdatePersonalInformationAdapter adapter(ApimExchangeExecutor executor) {
    return new ApimUpdatePersonalInformationAdapter(executor, new AccountIdentifierFormatter(11, 4, '0'));
  }

  private static String row(String cert, boolean success, String errors) {
    return "{\"success\":" + success + ",\"policy-no\":\"118\",\"cert-no\":\"" + cert
        + "\",\"env\":\"DB\",\"errors\":" + errors + "}";
  }

  private static String response(String rows) {
    return "{\"response\":{\"err-message\":\"\",\"data\":" + rows + "}}";
  }

  private static ApimResponseEnvelope<UpdateMemberInfoApimDataItem> envelope(String json)
      throws Exception {
    return JSON.readValue(
        json,
        JSON.getTypeFactory().constructParametricType(ApimResponseEnvelope.class, UpdateMemberInfoApimDataItem.class));
  }
}
