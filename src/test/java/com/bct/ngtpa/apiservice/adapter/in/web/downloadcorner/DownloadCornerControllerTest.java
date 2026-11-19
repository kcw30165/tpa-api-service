package com.bct.ngtpa.apiservice.adapter.in.web.downloadcorner;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.advice.ApiExceptionHandler;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieReader;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorEventLogger;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorResponseRenderer;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMappingRegistryConfig;
import com.bct.ngtpa.apiservice.adapter.in.web.error.PublicMessageResolver;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.dto.downloadcorner.DownloadCornerCategory;
import com.bct.ngtpa.apiservice.application.dto.downloadcorner.DownloadCornerItem;
import com.bct.ngtpa.apiservice.application.dto.downloadcorner.DownloadCornerResult;
import com.bct.ngtpa.apiservice.application.dto.downloadcorner.GetDownloadCornerCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.exception.DownloadCornerRetrievalException;
import com.bct.ngtpa.apiservice.application.port.in.GetDownloadCornerUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class DownloadCornerControllerTest {

  private static final String ROUTE = "/api/v1/download-corner";
  private static final AuthenticatedSessionId SESSION_ID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");

  @Test
  void usesAccountRefSessionAndAcceptLanguageWithResolvedContext() {
    AtomicReference<GetDownloadCornerCommand> captured = new AtomicReference<>();
    AtomicReference<String> accountRefHeader = new AtomicReference<>();
    AtomicReference<String> sessionCookie = new AtomicReference<>();
    GetDownloadCornerUseCase useCase =
        command -> {
          captured.set(command);
          return Mono.just(
              new DownloadCornerResult(
                  List.of(
                      new DownloadCornerCategory(
                          1,
                          "English category",
                          "中文類別",
                          List.of(
                              new DownloadCornerItem(
                                  1,
                                  "English item",
                                  "中文項目",
                                  "https://example.test/en.pdf",
                                  "https://example.test/zh.pdf",
                                  "en.pdf",
                                  "zh.pdf"))))));
        };
    AuthenticatedSessionCookieReader sessionReader =
        exchange -> {
          accountRefHeader.set(
              exchange
                  .getRequest()
                  .getHeaders()
                  .getFirst(RequestHeaderContextKeys.ACCOUNT_REF_HEADER));
          var cookie = exchange.getRequest().getCookies().getFirst("portal_sid");
          sessionCookie.set(cookie == null ? null : cookie.getValue());
          return Optional.of(SESSION_ID);
        };
    WebTestClient client = client(useCase, context("ACC-1"), sessionReader);

    client
        .get()
        .uri(ROUTE)
        .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, "ACC-1")
        .header(RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, "zh-HK")
        .cookie("portal_sid", SESSION_ID.value())
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.result.data.categories[0].categoryName")
        .isEqualTo("中文類別")
        .jsonPath("$.result.data.categories[0].items[0].itemName")
        .isEqualTo("中文項目")
        .jsonPath("$.result.data.categories[0].items[0].itemLink")
        .isEqualTo("https://example.test/zh.pdf");

    assertThat(accountRefHeader.get()).isEqualTo("ACC-1");
    assertThat(sessionCookie.get()).isEqualTo(SESSION_ID.value());
    assertThat(captured.get())
        .isEqualTo(
            new GetDownloadCornerCommand(
                "HK", "policy-1", "cert-1", "actor-1", ActorUserRole.ROLE_USER_MBR));
  }

  @Test
  void retrievalFailureReturnsTheFeature503Contract() {
    WebTestClient client =
        client(
            command -> Mono.error(DownloadCornerRetrievalException.systemFailure()),
            context("ACC-1"),
            exchange -> Optional.of(SESSION_ID));

    client
        .get()
        .uri(ROUTE)
        .header(RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, "en")
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(false)
        .jsonPath("$.errors[0].code")
        .isEqualTo(ErrorCodes.DOWNLOAD_CORNER_RETRIEVAL_UNAVAILABLE);
  }

  @Test
  void missingAccountRefUsesTheExistingSelectedAccountFailure() {
    WebTestClient client =
        client(
            command -> Mono.error(new AssertionError("use case must not run")),
            context(" "),
            exchange -> Optional.of(SESSION_ID));

    client
        .get()
        .uri(ROUTE)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors[0].code")
        .isEqualTo(ErrorCodes.MEMBER_CONTEXT_INVALID);
  }

  @Test
  void missingSessionUsesTheExistingAuthenticationFailure() {
    WebTestClient client =
        client(
            command -> Mono.error(new AssertionError("use case must not run")),
            context("ACC-1"),
            exchange -> Optional.empty());

    client
        .get()
        .uri(ROUTE)
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(false)
        .jsonPath("$.errors[0].code")
        .isEqualTo(ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED);
  }

  private static WebTestClient client(
      GetDownloadCornerUseCase useCase,
      PortalAccessContext context,
      AuthenticatedSessionCookieReader sessionCookieReader) {
    return WebTestClient.bindToController(
            new DownloadCornerController(
                useCase, resolver(context), sessionCookieReader, new DownloadCornerWebMapper()))
        .controllerAdvice(advice())
        .build();
  }

  private static CurrentPortalAccessContextResolver resolver(PortalAccessContext context) {
    return new CurrentPortalAccessContextResolver() {
      @Override
      public Mono<PortalAccessContext> current() {
        return Mono.just(context);
      }

      @Override
      public Mono<PortalAccessContext> currentOrEmpty() {
        return Mono.just(context);
      }
    };
  }

  private static PortalAccessContext context(String accountRef) {
    return new PortalAccessContext(
        new ActorContext("actor-1", ActorUserRole.ROLE_USER_MBR),
        new AccountContext(
            accountRef, "HK", "policy-1", "cert-1", "TRUST-1", "SCHEME-1", TermStatus.BLANK, null));
  }

  private static ApiExceptionHandler advice() {
    ErrorMessageResolver resolver = (code, locale, env, trust, scheme) -> code;
    ObjectMapper objectMapper = new ObjectMapper();
    return new ApiExceptionHandler(
        new ExceptionMappingRegistryConfig().exceptionMappingRegistry(),
        new PublicMessageResolver(resolver),
        new ErrorEventLogger(
            new LoggingSanitizer(objectMapper, new MaskingService(new MaskingProperties()))),
        new ErrorResponseRenderer());
  }
}
