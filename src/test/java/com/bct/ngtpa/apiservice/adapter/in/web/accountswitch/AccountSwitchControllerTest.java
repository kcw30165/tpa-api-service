package com.bct.ngtpa.apiservice.adapter.in.web.accountswitch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieReader;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSession;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.usecase.accountswitch.SwitchAccountUseCase;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedCsrfValidationException;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedPortalContextResolutionException;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.ValidateAuthenticatedSessionUseCase;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/**
 * Standalone controller tests (no Spring context), mirroring the confirmed LogoutController
 * sequencing: missing session cookie or missing CSRF both short-circuit to 401 before the session
 * is ever validated; a CSRF-validation failure maps to 403; an
 * AuthenticatedPortalContextResolutionException (session invalid OR target account not in
 * session -- both indistinguishable by design) maps to 401; a successful switch maps to 200 with
 * the mapped account fields.
 *
 * <p>{@code sessionValidation.validate(...)} returns {@code Mono<AuthenticatedSession>} (confirmed
 * via script 023 investigation). Stubbing it with {@code Mono.empty()} would mean the controller's
 * {@code flatMap(ignored -> ...)} function is never invoked at all (Reactor semantics: flatMap's
 * function only runs on an emitted value), so tests that expect the chain to proceed past
 * validation stub {@code Mono.just(mock(AuthenticatedSession.class))} instead -- the actual emitted
 * value is discarded by the controller either way, exactly like {@code LogoutController} does.
 */
class AccountSwitchControllerTest {

  private static final AuthenticatedSessionId SESSION_ID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
  private static final String TARGET_REF = "acc_xzxMWFN5aVMVyOTThpAe4wN0kObWotWb";
  private static final String CSRF = "csrf-token-value";

  private SwitchAccountUseCase useCase;
  private ValidateAuthenticatedSessionUseCase sessionValidation;
  private AuthenticatedSessionCookieReader cookieReader;
  private WebTestClient client;

  @BeforeEach
  void setUp() {
    useCase = mock(SwitchAccountUseCase.class);
    sessionValidation = mock(ValidateAuthenticatedSessionUseCase.class);
    cookieReader = mock(AuthenticatedSessionCookieReader.class);
    var controller =
        new AccountSwitchController(
            useCase, new AccountSwitchWebMapper(), sessionValidation, cookieReader);
    client = WebTestClient.bindToController(controller).build();
  }

  @Test
  void returns401WhenSessionCookieMissing() {
    when(cookieReader.read(any())).thenReturn(Optional.empty());

    client
        .post()
        .uri("/api/v1/account/switch")
        .header("X-XSRF-TOKEN", CSRF)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"accountRef\":\"" + TARGET_REF + "\"}")
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void returns401WhenCsrfHeaderMissing() {
    when(cookieReader.read(any())).thenReturn(Optional.of(SESSION_ID));

    client
        .post()
        .uri("/api/v1/account/switch")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"accountRef\":\"" + TARGET_REF + "\"}")
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void returns403WhenCsrfValidationFails() {
    when(cookieReader.read(any())).thenReturn(Optional.of(SESSION_ID));
    when(sessionValidation.validate(any()))
        .thenReturn(Mono.error(new AuthenticatedCsrfValidationException()));

    client
        .post()
        .uri("/api/v1/account/switch")
        .header("X-XSRF-TOKEN", CSRF)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"accountRef\":\"" + TARGET_REF + "\"}")
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Test
  void returns401WhenTargetAccountNotInSession() {
    when(cookieReader.read(any())).thenReturn(Optional.of(SESSION_ID));
    when(sessionValidation.validate(any()))
        .thenReturn(Mono.just(mock(AuthenticatedSession.class)));
    when(useCase.switchAccount(any()))
        .thenReturn(Mono.error(new AuthenticatedPortalContextResolutionException()));

    client
        .post()
        .uri("/api/v1/account/switch")
        .header("X-XSRF-TOKEN", CSRF)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"accountRef\":\"" + TARGET_REF + "\"}")
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void returns200WithMappedAccountOnSuccess() {
    var account =
        new AccountContext(TARGET_REF, "AA", "1111111111111111111111111", "1", "BB", "CC",
            TermStatus.BLANK, null);
    when(cookieReader.read(any())).thenReturn(Optional.of(SESSION_ID));
    when(sessionValidation.validate(any()))
        .thenReturn(Mono.just(mock(AuthenticatedSession.class)));
    when(useCase.switchAccount(any())).thenReturn(Mono.just(account));

    client
        .post()
        .uri("/api/v1/account/switch")
        .header("X-XSRF-TOKEN", CSRF)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"accountRef\":\"" + TARGET_REF + "\"}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(true)
        .jsonPath("$.result.accountRef")
        .isEqualTo(TARGET_REF)
        .jsonPath("$.result.trust")
        .isEqualTo("BB");
  }
}
