package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.advice.ApiExceptionHandler;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateResponseMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.personalinformation.PersonalInformationController;
import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdatePersonalInformationRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.validation.PersonalInformationFieldAccessChecker;
import com.bct.ngtpa.apiservice.adapter.in.web.validation.PersonalInformationFieldAccessValidator;
import com.bct.ngtpa.apiservice.adapter.in.web.validation.PersonalInformationUpdateYamlValidator;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.exception.PersonalInformationRetrievalException;
import com.bct.ngtpa.apiservice.application.port.in.GetPersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.port.in.UpdatePersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.application.usecase.personalinformation.PersonalInformationAccessConfigService;
import com.bct.ngtpa.apiservice.application.usecase.personalinformation.PersonalInformationConfigCacheService;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

/**
 * Decision O-1 end to end through the real WebFlux stack: a failed TRPGetMemberInfo lookup on the
 * PUT reaches the client as HTTP 503 SYSTEM_ERROR with the retrieval-unavailable code.
 */
class PersonalInformationPutRetrievalUnavailableContractTest {

  private static final AuthenticatedSessionId SID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
  private static final UpdatePersonalInformationRequest REQUEST =
      new UpdatePersonalInformationRequest(
          "1.0", false, Map.of("emailAddress", "member@example.test"));

  private final GetPersonalInformationUseCase retrieval = mock(GetPersonalInformationUseCase.class);
  private final PersonalInformationConfigCacheService cache =
      mock(PersonalInformationConfigCacheService.class);
  private final PersonalInformationFieldAccessValidator accessValidator =
      mock(PersonalInformationFieldAccessValidator.class);

  @Test
  void aFailedMemberInfoLookupOnThePutReturnsHttp503WithTheRetrievalUnavailableCode() {
    when(cache.read(any(), any(), any(), any())).thenReturn(Mono.just(Optional.empty()));
    when(retrieval.executeAndCache(any(), any(), any(), any()))
        .thenReturn(Mono.error(new IllegalStateException("downstream unavailable")));
    var checker =
        new PersonalInformationFieldAccessChecker(
            new PersonalInformationAccessConfigService(retrieval, cache), accessValidator);

    assertServiceUnavailable(client(checker));
  }

  @Test
  void aRetrievalExceptionFromTheAccessCheckReturnsHttp503() {
    var checker = mock(PersonalInformationFieldAccessChecker.class);
    when(checker.check(any(), any(), any(), any(), any()))
        .thenReturn(Mono.error(PersonalInformationRetrievalException.systemFailure()));

    assertServiceUnavailable(client(checker));
  }

  private static void assertServiceUnavailable(WebTestClient client) {
    client
        .put()
        .uri("/api/v1/personal-information")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(REQUEST)
        .exchange()
        .expectStatus()
        .isEqualTo(503)
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(false)
        .jsonPath("$.status")
        .isEqualTo("SYSTEM_ERROR")
        .jsonPath("$.errors[0].code")
        .isEqualTo("err.personal-information.retrieval.unavailable")
        .jsonPath("$.errors[0].type")
        .isEqualTo("SYSTEM")
        .jsonPath("$.errors[0].message")
        .doesNotExist();
  }

  private WebTestClient client(PersonalInformationFieldAccessChecker checker) {
    var controller =
        new PersonalInformationController(
            mock(GetPersonalInformationUseCase.class),
            mock(PersonalInformationWebMapper.class),
            resolver(context("ACC-123")),
            mock(UpdatePersonalInformationUseCase.class),
            mock(PersonalInformationUpdateWebMapper.class),
            mock(PersonalInformationUpdateResponseMapper.class),
            mock(PersonalInformationUpdateYamlValidator.class),
            exchange -> Optional.of(SID),
            checker);
    var handler =
        new ApiExceptionHandler(
            (code, locale, accountEnv, trustCode, schemeType) -> "resolved-" + code,
            new LoggingSanitizer(new ObjectMapper(), new MaskingService(new MaskingProperties())));
    WebFilter requestHeaderContext =
        (serverExchange, chain) ->
            chain
                .filter(serverExchange)
                .contextWrite(
                    reactorContext ->
                        reactorContext.put(
                            RequestHeaderContextKeys.CONTEXT_KEY,
                            new RequestHeaderContext("ACC-123", "req-9", "en")));
    return WebTestClient.bindToController(controller)
        .controllerAdvice(handler)
        .webFilter(requestHeaderContext)
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
        new ActorContext("user-1", ActorUserRole.ROLE_USER_MBR),
        new AccountContext(
            accountRef, "JP", "policy-1", "cert-1", "JPM", "OE", TermStatus.BLANK, null));
  }
}
