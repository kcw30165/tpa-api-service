package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.auth.login.account.LoginAccountAuthorizationCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.login.account.LoginAccountAuthorizationResult;
import com.bct.ngtpa.apiservice.application.dto.auth.login.account.LoginAccountRecord;
import com.bct.ngtpa.apiservice.application.dto.auth.login.account.LoginAccountRef;
import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.LoginPreAuthContext;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.CreateOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.CreateOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.InvalidateOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpVerificationOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.ReplaceOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.ReplaceOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.VerifyOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.VerifyOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.port.in.EstablishAuthenticatedLoginSessionUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionCancellationPort;
import com.bct.ngtpa.apiservice.application.port.out.LoginAccountAuthorizationPort;
import com.bct.ngtpa.apiservice.application.port.out.LoginOtpVerifiedSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.LoginProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.account.GetLoginAccountsUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.account.LoginAccountRecordValidationService;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.account.LoginAccountReferenceAssignmentService;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.LoginProtectedAuthFlowContextResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.session.EstablishAuthenticatedLoginSessionCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.session.EstablishAuthenticatedLoginSessionResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.session.EstablishAuthenticatedLoginSessionStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LoginOtpAuthenticatedSessionTransitionTest {
  private static final AuthenticatedSessionId SID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");

  @Test
  void validOtpAndAccountsEstablishAuthenticatedSession() {
    AtomicReference<EstablishAuthenticatedLoginSessionCommand> transitionCommand =
        new AtomicReference<>();
    LoginOtpVerifyService service =
        service(
            command -> {
              transitionCommand.set(command);
              return Mono.just(
                  EstablishAuthenticatedLoginSessionResult.established(
                      SID, "authenticated_csrf_token"));
            });

    StepVerifier.create(service.verify(command()))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(VerifyLoginOtpStatus.ACCOUNTS_READY);
              assertThat(result.authenticatedSessionId()).isEqualTo(SID);
              assertThat(result.clearAuthFlowCookie()).isTrue();
            })
        .verifyComplete();

    assertThat(transitionCommand.get().actorContext())
        .isEqualTo(new ActorContext("member", ActorUserRole.ROLE_USER_MBR));
    assertThat(transitionCommand.get().csrfTokenHash()).isEqualTo("csrf_hash");
    assertThat(transitionCommand.get().accounts()).hasSize(1);
  }

  @Test
  void transitionFailureDoesNotExposeAccountsOrSession() {
    LoginOtpVerifyService service =
        service(
            command ->
                Mono.just(
                    EstablishAuthenticatedLoginSessionResult.failed(
                        EstablishAuthenticatedLoginSessionStatus.SESSION_CREATION_FAILED)));

    StepVerifier.create(service.verify(command()))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(VerifyLoginOtpStatus.SESSION_CREATION_FAILED);
              assertThat(result.accounts()).isEmpty();
              assertThat(result.authenticatedSessionId()).isNull();
            })
        .verifyComplete();
  }

  private LoginOtpVerifyService service(EstablishAuthenticatedLoginSessionUseCase transition) {
    LoginPreAuthContext context =
        new LoginPreAuthContext(
            "member", "ROLE_USER_MBR", Map.of(), Instant.EPOCH, "v1", null, null, null);
    LoginProtectedAuthFlowContextLoader loader =
        command ->
            Mono.just(
                LoginProtectedAuthFlowContextResult.valid(
                    context, "otp_active", null, "csrf_hash"));
    OtpChallengePort otp =
        new OtpChallengePort() {
          @Override
          public Mono<VerifyOtpChallengeResult> verifyChallenge(VerifyOtpChallengeCommand command) {
            return Mono.just(
                new VerifyOtpChallengeResult(
                    command.otpChallengeId(), OtpVerificationOutcome.VALID, 4, null));
          }

          @Override
          public Mono<CreateOtpChallengeResult> createChallenge(CreateOtpChallengeCommand command) {
            return Mono.error(new UnsupportedOperationException());
          }

          @Override
          public Mono<ReplaceOtpChallengeResult> replaceChallenge(
              ReplaceOtpChallengeCommand command) {
            return Mono.error(new UnsupportedOperationException());
          }

          @Override
          public Mono<Void> invalidateChallenge(InvalidateOtpChallengeCommand command) {
            return Mono.empty();
          }
        };
    LoginOtpVerifiedSessionPort verified = (sessionId, requestId) -> Mono.empty();
    AuthFlowSessionCancellationPort cancellation =
        (sessionId, reason, now) -> Mono.error(new UnsupportedOperationException());
    LoginAccountAuthorizationPort accountPort =
        (LoginAccountAuthorizationCommand command) ->
            Mono.just(
                LoginAccountAuthorizationResult.available(
                    List.of(new LoginAccountRecord("DB", "DBA", "8802", "OE", 1, null, null))));
    GetLoginAccountsUseCase accounts = new GetLoginAccountsUseCase(accountPort);
    LoginAccountRecordValidationService validation =
        new LoginAccountRecordValidationService(11, 4, '0');
    LoginAccountReferenceAssignmentService assignment =
        new LoginAccountReferenceAssignmentService(
            () -> new LoginAccountRef("acc_abcdefghijklmnopqrstuvwx"), 8);

    return LoginOtpVerifyTestSupport.newService(
        loader,
        otp,
        verified,
        cancellation,
        accounts,
        validation,
        assignment,
        transition,
        Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
  }

  private VerifyLoginOtpCommand command() {
    return new VerifyLoginOtpCommand(
        "afs_opaque", "csrf", "123456", "idem_idem", "https://portal", null, "en", "req");
  }
}
