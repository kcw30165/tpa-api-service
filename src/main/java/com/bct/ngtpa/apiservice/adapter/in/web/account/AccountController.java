package com.bct.ngtpa.apiservice.adapter.in.web.account;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieReader;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.HttpStatusClassification;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.usecase.account.AccountNotInSessionException;
import com.bct.ngtpa.apiservice.application.usecase.account.GetAccountCommand;
import com.bct.ngtpa.apiservice.application.usecase.account.GetAccountUseCase;
import com.bct.ngtpa.apiservice.application.usecase.account.GetAccountsCommand;
import com.bct.ngtpa.apiservice.application.usecase.account.GetAccountsUseCase;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedCsrfValidationException;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedPortalContextResolutionException;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.ValidateAuthenticatedSessionCommand;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.ValidateAuthenticatedSessionUseCase;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Stateless account lookup endpoints: resolve display metadata for one or all accounts within the
 * current authenticated session. No session mutation occurs; the frontend continues sending the
 * {@code Account-Ref} header on every subsequent authenticated call as today.
 *
 * <p>Both endpoints share the same cookie -> CSRF -> session-validation sequencing, mirroring
 * {@code LogoutController}. For {@code POST /api/v1/account}, a target account not present in the
 * session's account list (422) is distinguished from an invalid/expired session (401). For {@code
 * POST /api/v1/accounts}, there is no not-found case -- a valid session simply returns however many
 * accounts it has, including zero.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public final class AccountController {

  private static final String SOURCE_SERVER = "SERVER";

  private final GetAccountUseCase getAccountUseCase;
  private final GetAccountsUseCase getAccountsUseCase;
  private final AccountWebMapper mapper;
  private final ValidateAuthenticatedSessionUseCase sessionValidation;
  private final AuthenticatedSessionCookieReader sessionCookieReader;

  @PostMapping("/account")
  public Mono<ResponseEntity<MutationResponse<AccountResultResponse>>> getAccount(
      @RequestHeader(name = RequestHeaderContextKeys.XSRF_TOKEN_HEADER, required = false)
          String csrf,
      @RequestHeader(name = RequestCorrelation.REQUEST_ID_HEADER, required = false)
          String requestId,
      @Valid @RequestBody AccountRequest request,
      ServerWebExchange exchange,
      ServerHttpResponse response) {
    applyResponseHeaders(response, requestId);
    return requireSession(exchange, csrf, requestId)
        .flatMap(
            sessionId ->
                getAccountUseCase.getAccount(
                    new GetAccountCommand(sessionId, request.accountRef())))
        .map(account -> ResponseEntity.ok(mapper.toAccountResponse(account)))
        .onErrorResume(AuthenticatedCsrfValidationException.class, ignored -> csrfInvalid())
        .onErrorResume(AccountNotInSessionException.class, ignored -> accountNotFound())
        .onErrorResume(
            AuthenticatedPortalContextResolutionException.class, ignored -> unauthorized());
  }

  @PostMapping("/accounts")
  public Mono<ResponseEntity<MutationResponse<AccountsResultResponse>>> getAccounts(
      @RequestHeader(name = RequestHeaderContextKeys.XSRF_TOKEN_HEADER, required = false)
          String csrf,
      @RequestHeader(name = RequestCorrelation.REQUEST_ID_HEADER, required = false)
          String requestId,
      ServerWebExchange exchange,
      ServerHttpResponse response) {
    applyResponseHeaders(response, requestId);
    return requireSession(exchange, csrf, requestId)
        .flatMap(sessionId -> getAccountsUseCase.getAccounts(new GetAccountsCommand(sessionId)))
        .map(accounts -> ResponseEntity.ok(mapper.toAccountsResponse(accounts)))
        .onErrorResume(AuthenticatedCsrfValidationException.class, ignored -> csrfInvalid())
        .onErrorResume(
            AuthenticatedPortalContextResolutionException.class, ignored -> unauthorized());
  }

  private Mono<AuthenticatedSessionId> requireSession(
      ServerWebExchange exchange, String csrf, String requestId) {
    Optional<AuthenticatedSessionId> sessionId = sessionCookieReader.read(exchange);
    if (sessionId.isEmpty() || !hasText(csrf)) {
      return Mono.error(new AuthenticatedPortalContextResolutionException());
    }
    return sessionValidation
        .validate(new ValidateAuthenticatedSessionCommand(sessionId.get(), csrf, clean(requestId)))
        .thenReturn(sessionId.get());
  }

  private void applyResponseHeaders(ServerHttpResponse response, String requestId) {
    response.getHeaders().setCacheControl(CacheControl.noStore());
    if (hasText(requestId)) {
      response.getHeaders().set(RequestCorrelation.REQUEST_ID_HEADER, requestId.trim());
    }
  }

  private <T> Mono<ResponseEntity<MutationResponse<T>>> csrfInvalid() {
    return Mono.just(
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(
                MutationResponse.failure(
                    ApiStatus.VALIDATION_FAILED,
                    List.of(
                        ApiError.form(
                            AccountErrorKey.CSRF_INVALID.value(),
                            null,
                            List.of("PAGE"),
                            SOURCE_SERVER)))));
  }

  private <T> Mono<ResponseEntity<MutationResponse<T>>> accountNotFound() {
    MutationResponse<T> body =
        MutationResponse.failure(
            ApiStatus.BUSINESS_REJECTED,
            List.of(
                ApiError.business(
                    AccountErrorKey.ACCOUNT_NOT_FOUND.value(),
                    null,
                    List.of("PAGE"),
                    SOURCE_SERVER)));
    return Mono.just(
        ResponseEntity.status(HttpStatusClassification.classify(body.status())).body(body));
  }

  private <T> Mono<ResponseEntity<MutationResponse<T>>> unauthorized() {
    return Mono.just(
        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(
                MutationResponse.failure(
                    ApiStatus.BUSINESS_REJECTED,
                    List.of(
                        new ApiError(
                            "AUTHENTICATION_REQUIRED",
                            ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED,
                            null,
                            List.of("PAGE"),
                            "ERROR",
                            SOURCE_SERVER)))));
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String clean(String value) {
    return hasText(value) ? value.trim() : null;
  }
}
