package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.auth.login.account.*;
import com.bct.ngtpa.apiservice.application.dto.auth.login.handoff.*;
import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.LoginIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.*;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.*;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.in.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.service.auth.login.otp.verify.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.account.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.replay.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.session.*;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public final class LoginOtpVerifyService implements VerifyLoginOtpUseCase {
  private static final Logger LOG = LoggerFactory.getLogger(LoginOtpVerifyService.class);
  private final LoginProtectedAuthFlowContextLoader loader;
  private final LoginOtpVerifyPersistencePort otpVerifyPersistence;
  private final AuthFlowSessionMutationLockPort mutationLock;
  private final AuthFlowSessionPort sessions;
  private final SessionRevisionGenerator revisions;
  private final SecureLoginOtpVerifyReferenceGenerator references;
  private final LoginOtpVerifyRequestHasher requestHasher;
  private final Duration lockLease;
  private final LoginOtpVerifiedSessionPort verifiedSession;
  private final AuthFlowSessionCancellationPort cancellation;
  private final GetLoginAccountsUseCase accountLookup;
  private final LoginAccountRecordValidationService accountValidation;
  private final LoginAccountReferenceAssignmentService accountAssignment;
  private final EstablishAuthenticatedLoginSessionUseCase transition;
  private final Clock clock;
  private final RecoverCommittedLoginUseCase committedReplay;

  public LoginOtpVerifyService(
      LoginProtectedAuthFlowContextLoader l,
      LoginOtpVerifyPersistencePort persistence,
      AuthFlowSessionMutationLockPort mutationLock,
      AuthFlowSessionPort sessions,
      SessionRevisionGenerator revisions,
      SecureLoginOtpVerifyReferenceGenerator references,
      LoginOtpVerifyRequestHasher requestHasher,
      Duration lockLease,
      LoginOtpVerifiedSessionPort v,
      AuthFlowSessionCancellationPort c,
      GetLoginAccountsUseCase g,
      LoginAccountRecordValidationService validation,
      LoginAccountReferenceAssignmentService assignment,
      EstablishAuthenticatedLoginSessionUseCase transition,
      Clock k,
      RecoverCommittedLoginUseCase committedReplay) {
    loader = Objects.requireNonNull(l);
    otpVerifyPersistence = Objects.requireNonNull(persistence);
    this.mutationLock = Objects.requireNonNull(mutationLock);
    this.sessions = Objects.requireNonNull(sessions);
    this.revisions = Objects.requireNonNull(revisions);
    this.references = Objects.requireNonNull(references);
    this.requestHasher = Objects.requireNonNull(requestHasher);
    this.lockLease = Objects.requireNonNull(lockLease);
    verifiedSession = Objects.requireNonNull(v);
    cancellation = Objects.requireNonNull(c);
    accountLookup = Objects.requireNonNull(g);
    accountValidation = Objects.requireNonNull(validation);
    accountAssignment = Objects.requireNonNull(assignment);
    this.transition = Objects.requireNonNull(transition);
    clock = Objects.requireNonNull(k);
    this.committedReplay = Objects.requireNonNull(committedReplay);
  }

  public LoginOtpVerifyService(
      LoginProtectedAuthFlowContextLoader l,
      LoginOtpVerifyPersistencePort persistence,
      AuthFlowSessionMutationLockPort mutationLock,
      AuthFlowSessionPort sessions,
      SessionRevisionGenerator revisions,
      SecureLoginOtpVerifyReferenceGenerator references,
      LoginOtpVerifyRequestHasher requestHasher,
      Duration lockLease,
      LoginOtpVerifiedSessionPort v,
      AuthFlowSessionCancellationPort c,
      GetLoginAccountsUseCase g,
      LoginAccountRecordValidationService validation,
      LoginAccountReferenceAssignmentService assignment,
      EstablishAuthenticatedLoginSessionUseCase transition,
      Clock k) {
    this(
        l,
        persistence,
        mutationLock,
        sessions,
        revisions,
        references,
        requestHasher,
        lockLease,
        v,
        c,
        g,
        validation,
        assignment,
        transition,
        k,
        command -> Mono.just(RecoverCommittedLoginResult.failed()));
  }

  public Mono<VerifyLoginOtpResult> verify(VerifyLoginOtpCommand c) {
    String reservationHash = requestHasher.hash();
    LoginIdempotencyKey publicKey = new LoginIdempotencyKey(c.idempotencyKey());
    Instant now = clock.instant();
    var reserve =
        new ReserveLoginOtpVerifyCommand(
            publicKey,
            reservationHash,
            references.handoff(),
            references.operation(),
            now,
            now.plusSeconds(600));
    return otpVerifyPersistence
        .reserveOrLoad(reserve)
        .flatMap(
            reserved -> {
              if (reserved.outcome() == LoginOtpVerifyReservationOutcome.REQUEST_HASH_MISMATCH
                  || reserved.reservation() == null)
                return Mono.just(result(VerifyLoginOtpStatus.SYSTEM_FAILED, null, false));
              var reservation = reserved.reservation();
              if (reservation.proof() != null) return recoverCommittedLogin(reservation.proof(), c);
              return loader
                  .load(
                      new LoginProtectedAuthFlowContextCommand(
                          c.authFlowSessionId(),
                          c.csrfToken(),
                          c.origin(),
                          c.referer(),
                          c.correlationId(),
                          AuthFlowStatus.OTP_SENT,
                          true))
                  .flatMap(
                      x ->
                          x.isValid()
                              ? verifyActive(c, x, publicKey, reservationHash, reservation)
                              : Mono.just(
                                  result(
                                      x.status() == LoginProtectedAuthFlowContextStatus.CSRF_INVALID
                                          ? VerifyLoginOtpStatus.CSRF_INVALID
                                          : VerifyLoginOtpStatus.AUTH_FLOW_INVALID,
                                      null,
                                      false)));
            })
        .doOnNext(r -> LOG.info("event=login.otp.verify.result status={}", r.status()))
        .doOnError(
            e ->
                LOG.error(
                    "event=login.otp.verify.failure exceptionType={} message={} correlationId={}",
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    c.correlationId(),
                    e))
        .onErrorReturn(result(VerifyLoginOtpStatus.SYSTEM_FAILED, null, false));
  }

  private Mono<VerifyLoginOtpResult> verifyActive(
      VerifyLoginOtpCommand c,
      LoginProtectedAuthFlowContextResult x,
      LoginIdempotencyKey publicKey,
      String reservationHash,
      LoginOtpVerifyReservation reservation) {
    AuthFlowSessionId sid = new AuthFlowSessionId(c.authFlowSessionId());
    AuthenticatedLoginHandoffReference handoff = reservation.handoffReference();
    LoginOtpVerificationOperationReference operation = reservation.operationReference();
    return mutationLock
        .acquire(sid, lockLease)
        .flatMap(
            lock -> {
              if (!lock.acquired()) {
                return Mono.just(result(VerifyLoginOtpStatus.SYSTEM_FAILED, null, false));
              }
              Mono<VerifyLoginOtpResult> operationFlow =
                  sessions
                      .loadSnapshot(sid)
                      .flatMap(
                          snapshot -> {
                            if (snapshot.isEmpty()) {
                              return Mono.just(
                                  result(VerifyLoginOtpStatus.AUTH_FLOW_INVALID, null, false));
                            }
                            var command =
                                new RevisionedLoginOtpVerifyCommand(
                                    lock,
                                    snapshot.orElseThrow(),
                                    x.activeOtpChallengeId(),
                                    reservationHash,
                                    handoff,
                                    operation,
                                    new RedactedSubmittedOtp(c.otpCode()),
                                    clock.instant(),
                                    revisions.generate());
                            return otpVerifyPersistence
                                .verify(command)
                                .flatMap(
                                    r ->
                                        switch (r.outcome()) {
                                          case VERIFIED_AND_CONSUMED ->
                                              /* Authoritative identities are r.proof().handoffReference()
                                               * and r.proof().operationReference(); the same proof is
                                               * forwarded intact into continuation. */
                                              otpVerifyPersistence
                                                  .recordVerificationCommitted(
                                                      new RecordLoginOtpVerifyCommittedCommand(
                                                          publicKey,
                                                          reservationHash,
                                                          r.proof(),
                                                          r.outcome(),
                                                          r.remainingAttempts()))
                                                  .then(
                                                      verifiedSession.markVerified(
                                                          c.authFlowSessionId(), c.correlationId()))
                                                  /* First-request continuation remains the established
                                                   * continueToAccounts(c, x) boundary, extended only by the
                                                   * authoritative committed proof required by 001A-6. */
                                                  .then(
                                                      Mono.defer(
                                                          () ->
                                                              continueToAccounts(c, x, r.proof())));
                                          case INVALID ->
                                              otpVerifyPersistence
                                                  .recordConclusiveFailure(
                                                      publicKey,
                                                      reservationHash,
                                                      r.outcome(),
                                                      r.remainingAttempts())
                                                  .thenReturn(
                                                      result(
                                                          VerifyLoginOtpStatus.INVALID,
                                                          r.remainingAttempts(),
                                                          false));
                                          case EXPIRED ->
                                              Mono.just(
                                                  result(
                                                      VerifyLoginOtpStatus.EXPIRED, null, false));
                                          case LOCKED ->
                                              cancellation
                                                  .cancel(
                                                      sid,
                                                      AuthFlowSessionCancellationReason
                                                          .SECURITY_REJECTION,
                                                      clock.instant())
                                                  .thenReturn(
                                                      result(
                                                          VerifyLoginOtpStatus
                                                              .VERIFY_ATTEMPTS_EXCEEDED,
                                                          0,
                                                          true));
                                          case ALREADY_COMMITTED ->
                                              recoverCommittedLogin(r.proof(), c);
                                          default ->
                                              Mono.just(
                                                  result(
                                                      VerifyLoginOtpStatus.SYSTEM_FAILED,
                                                      null,
                                                      false));
                                        });
                          })
                      .switchIfEmpty(
                          Mono.just(result(VerifyLoginOtpStatus.SYSTEM_FAILED, null, false)));
              return operationFlow
                  .materialize()
                  .flatMap(
                      signal -> mutationLock.release(lock).onErrorReturn(false).thenReturn(signal))
                  .dematerialize();
            });
  }

  private Mono<VerifyLoginOtpResult> continueToAccounts(
      VerifyLoginOtpCommand c,
      LoginProtectedAuthFlowContextResult x,
      LoginOtpConsumptionProof proof) {
    var context = x.context();
    return accountLookup
        .getAccounts(
            new GetLoginAccountsCommand(
                context.normalizedUsername(),
                c.correlationId(),
                ActorUserRole.valueOf(context.userRole())))
        .flatMap(
            auth -> {
              if (auth.status() == LoginAccountAuthorizationStatus.UNAVAILABLE)
                return Mono.just(
                    result(VerifyLoginOtpStatus.ACCOUNT_LIST_UNAVAILABLE, null, false));
              var validated = accountValidation.validate(auth.accounts());
              if (validated.status() == LoginAccountValidationStatus.ZERO_VALID_RECORDS)
                return Mono.just(result(VerifyLoginOtpStatus.ZERO_VALID_ACCOUNTS, null, false));
              var assigned = accountAssignment.assign(validated.accounts());
              var actor =
                  new ActorContext(
                      context.normalizedUsername(), ActorUserRole.valueOf(context.userRole()));
              var command =
                  new EstablishAuthenticatedLoginSessionCommand(
                      new AuthFlowSessionId(c.authFlowSessionId()),
                      actor,
                      assigned.accounts(),
                      x.csrfTokenHash(),
                      c.correlationId(),
                      proof.handoffReference(),
                      proof.operationReference(),
                      proof.reservationHash(),
                      proof);
              return transition
                  .establish(command)
                  .doOnNext(
                      established -> {
                        if (established.status()
                            != EstablishAuthenticatedLoginSessionStatus.ESTABLISHED) {
                          LOG.error(
                              "event=login.otp.verify.continuation.rejected stage=authenticated-session-establishment status={} accountCount={} correlationId={}",
                              established.status(),
                              assigned.accounts().size(),
                              c.correlationId());
                        }
                      })
                  .map(
                      established ->
                          established.status()
                                  == EstablishAuthenticatedLoginSessionStatus.ESTABLISHED
                              ? new VerifyLoginOtpResult(
                                  VerifyLoginOtpStatus.ACCOUNTS_READY,
                                  null,
                                  established.clearAuthFlowCookie(),
                                  assigned.accounts(),
                                  established.authenticatedSessionId(),
                                  established.authenticatedCsrfToken())
                              : result(VerifyLoginOtpStatus.SESSION_CREATION_FAILED, null, false));
            });
  }

  // Superseded source-contract trace: case ALREADY_COMMITTED -> recoverCommittedLogin(r.proof(), c,
  // x)
  private Mono<VerifyLoginOtpResult> recoverCommittedLogin(
      LoginOtpConsumptionProof proof, VerifyLoginOtpCommand c) {
    if (proof == null) return Mono.just(result(VerifyLoginOtpStatus.SYSTEM_FAILED, null, false));
    return committedReplay
        .recover(new RecoverCommittedLoginCommand(proof, c.correlationId()))
        .map(
            recovered ->
                recovered.recovered()
                    ? new VerifyLoginOtpResult(
                        VerifyLoginOtpStatus.ACCOUNTS_READY,
                        null,
                        true,
                        recovered.accounts(),
                        recovered.authenticatedSessionId(),
                        recovered.authenticatedCsrfToken())
                    : result(VerifyLoginOtpStatus.SYSTEM_FAILED, null, false))
        .onErrorReturn(result(VerifyLoginOtpStatus.SYSTEM_FAILED, null, false));
  }

  private VerifyLoginOtpResult result(VerifyLoginOtpStatus s, Integer remaining, boolean clear) {
    return new VerifyLoginOtpResult(s, remaining, clear, List.of(), null, null);
  }
}
