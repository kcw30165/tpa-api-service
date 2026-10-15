package com.bct.ngtpa.apiservice.adapter.in.web.account;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieReader;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSession;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.usecase.account.AccountNotInSessionException;
import com.bct.ngtpa.apiservice.application.usecase.account.GetAccountUseCase;
import com.bct.ngtpa.apiservice.application.usecase.account.GetAccountsUseCase;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedCsrfValidationException;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedPortalContextResolutionException;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.ValidateAuthenticatedSessionUseCase;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/**
 * Standalone controller tests (no Spring context) covering both {@code POST /api/v1/account} and
 * {@code POST /api/v1/accounts}, mirroring the confirmed LogoutController sequencing.
 */
class AccountControllerTest {

  private static final AuthenticatedSessionId SESSION_ID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
  private static final String TARGET_REF = "acc_xzxMWFN5aVMVyOTThpAe4wN0kObWotWb";
  private static final String CSRF = "csrf-token-value";

  private GetAccountUseCase getAccountUseCase;
  private GetAccountsUseCase getAccountsUseCase;
  private ValidateAuthenticatedSessionUseCase sessionValidation;
  private AuthenticatedSessionCookieReader cookieReader;
  private WebTestClient client;

  @BeforeEach
  void setUp() {
    getAccountUseCase = mock(GetAccountUseCase.class);
    getAccountsUseCase = mock(GetAccountsUseCase.class);
    sessionValidation = mock(ValidateAuthenticatedSessionUseCase.class);
    cookieReader = mock(AuthenticatedSessionCookieReader.class);
    var controller =
        new AccountController(
            getAccountUseCase,
            getAccountsUseCase,
            new AccountWebMapper(),
            sessionValidation,
            cookieReader);
    client = WebTestClient.bindToController(controller).build();
  }

  // ----- POST /api/v1/account --------------------------------------------

  @Test
  void getAccount_returns401WhenSessionCookieMissing() {
    when(cookieReader.read(any())).thenReturn(Optional.empty());

    client
        .post()
        .uri("/api/v1/account")
        .header("X-XSRF-TOKEN", CSRF)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"accountRef\":\"" + TARGET_REF + "\"}")
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void getAccount_returns401WhenCsrfHeaderMissing() {
    when(cookieReader.read(any())).thenReturn(Optional.of(SESSION_ID));

    client
        .post()
        .uri("/api/v1/account")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"accountRef\":\"" + TARGET_REF + "\"}")
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void getAccount_returns403WhenCsrfValidationFails() {
    when(cookieReader.read(any())).thenReturn(Optional.of(SESSION_ID));
    when(sessionValidation.validate(any()))
        .thenReturn(Mono.error(new AuthenticatedCsrfValidationException()));

    client
        .post()
        .uri("/api/v1/account")
        .header("X-XSRF-TOKEN", CSRF)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"accountRef\":\"" + TARGET_REF + "\"}")
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Test
  void getAccount_returns401WhenSessionItselfInvalid() {
    when(cookieReader.read(any())).thenReturn(Optional.of(SESSION_ID));
    when(sessionValidation.validate(any()))
        .thenReturn(Mono.error(new AuthenticatedPortalContextResolutionException()));

    client
        .post()
        .uri("/api/v1/account")
        .header("X-XSRF-TOKEN", CSRF)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"accountRef\":\"" + TARGET_REF + "\"}")
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void getAccount_returns422WhenTargetAccountNotInSession() {
    when(cookieReader.read(any())).thenReturn(Optional.of(SESSION_ID));
    when(sessionValidation.validate(any())).thenReturn(Mono.just(mock(AuthenticatedSession.class)));
    when(getAccountUseCase.getAccount(any()))
        .thenReturn(Mono.error(new AccountNotInSessionException()));

    client
        .post()
        .uri("/api/v1/account")
        .header("X-XSRF-TOKEN", CSRF)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"accountRef\":\"" + TARGET_REF + "\"}")
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @Test
  void getAccount_returns200WithMappedAccountOnSuccess() {
    var account =
        new AccountContext(
            TARGET_REF, "AA", "1111111111111111111111111", "1", "BB", "CC", TermStatus.BLANK, null);
    when(cookieReader.read(any())).thenReturn(Optional.of(SESSION_ID));
    when(sessionValidation.validate(any())).thenReturn(Mono.just(mock(AuthenticatedSession.class)));
    when(getAccountUseCase.getAccount(any())).thenReturn(Mono.just(account));

    client
        .post()
        .uri("/api/v1/account")
        .header("X-XSRF-TOKEN", CSRF)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"accountRef\":\"" + TARGET_REF + "\"}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(true)
        .jsonPath("$.result.data.accountRef")
        .isEqualTo(TARGET_REF)
        .jsonPath("$.result.data.trustCode")
        .isEqualTo("BB");
  }

  // ----- POST /api/v1/accounts --------------------------------------------

  @Test
  void getAccounts_returns401WhenSessionCookieMissing() {
    when(cookieReader.read(any())).thenReturn(Optional.empty());

    client
        .post()
        .uri("/api/v1/accounts")
        .header("X-XSRF-TOKEN", CSRF)
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void getAccounts_returns401WhenCsrfHeaderMissing() {
    when(cookieReader.read(any())).thenReturn(Optional.of(SESSION_ID));

    client.post().uri("/api/v1/accounts").exchange().expectStatus().isUnauthorized();
  }

  @Test
  void getAccounts_returns403WhenCsrfValidationFails() {
    when(cookieReader.read(any())).thenReturn(Optional.of(SESSION_ID));
    when(sessionValidation.validate(any()))
        .thenReturn(Mono.error(new AuthenticatedCsrfValidationException()));

    client
        .post()
        .uri("/api/v1/accounts")
        .header("X-XSRF-TOKEN", CSRF)
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Test
  void getAccounts_returns401WhenSessionItselfInvalid() {
    when(cookieReader.read(any())).thenReturn(Optional.of(SESSION_ID));
    when(sessionValidation.validate(any()))
        .thenReturn(Mono.error(new AuthenticatedPortalContextResolutionException()));

    client
        .post()
        .uri("/api/v1/accounts")
        .header("X-XSRF-TOKEN", CSRF)
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void getAccounts_returns200WithMappedAccountListOnSuccess() {
    var a1 =
        new AccountContext(
            "acc_CEoNbrhgjmYgu2YgrdoqfB8psucGR7-X",
            "TB",
            "00000000112",
            "0582",
            "HKBU",
            "OE",
            TermStatus.BLANK,
            null);
    var a2 =
        new AccountContext(
            "acc_ibSsUgtP8zeG8AxixnOkUkt3GZQ6v_Ce",
            "TB",
            "00000004203",
            "0186",
            "MSIG",
            "OE",
            TermStatus.BLANK,
            null);
    when(cookieReader.read(any())).thenReturn(Optional.of(SESSION_ID));
    when(sessionValidation.validate(any())).thenReturn(Mono.just(mock(AuthenticatedSession.class)));
    when(getAccountsUseCase.getAccounts(any())).thenReturn(Mono.just(List.of(a1, a2)));

    client
        .post()
        .uri("/api/v1/accounts")
        .header("X-XSRF-TOKEN", CSRF)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(true)
        .jsonPath("$.result.data.accounts.length()")
        .isEqualTo(2)
        .jsonPath("$.result.data.accounts[0].trustCode")
        .isEqualTo("HKBU")
        .jsonPath("$.result.data.accounts[1].trustCode")
        .isEqualTo("MSIG");
  }
}
