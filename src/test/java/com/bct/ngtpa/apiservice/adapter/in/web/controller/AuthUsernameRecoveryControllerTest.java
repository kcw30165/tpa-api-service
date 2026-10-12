package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.dto.UsernameRecoveryStartRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.mapper.UsernameRecoveryCancelWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.mapper.UsernameRecoveryRequestWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.mapper.UsernameRecoveryStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.policy.UsernameRecoveryCancelResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.policy.UsernameRecoveryRequestResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.policy.UsernameRecoveryStartResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.session.cookie.HttpSessionCookieAdapter;
import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelResult;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryDestinationType;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestResult;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoverySchemeProjection;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookiePolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieSameSite;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.in.CancelUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.RequestUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.StartUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryCancelException;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryRequestException;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartException;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthUsernameRecoveryControllerTest {

  private static final String COOKIE_PATH = "/api/v1/auth/username-recovery";
  private static final Duration TTL = Duration.ofSeconds(600);

  @Test
  void startSuccessPublishesExactPathCookiesAndInvokesWithStaffNoLanguageAndIdempotency() {
    var start =
        new RecordingStartUseCase(Mono.just(sessionCreated("afs_opaque", "csrf-value", "sch_1")));
    var controller = controller(start, neverRequest(), neverCancel());
    var response = new MockServerHttpResponse();
    var request =
        new UsernameRecoveryStartRequest(
            "STAFF_NO", "3030609", "04/07/1978", "member@example.com", null, "cap_verify");

    StepVerifier.create(controller.start(request, "zh-HK", "req_abc", "idem_start_123", response))
        .assertNext(
            entity -> {
              assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
              assertThat(entity.getBody().success()).isTrue();
              assertThat(entity.getBody().result().nextStep())
                  .isEqualTo(WorkflowNextStep.SELECT_ACCOUNT);
            })
        .verifyComplete();

    assertThat(start.calls).hasSize(1);
    UsernameRecoveryStartCommand command = start.calls.get(0);
    assertThat(command.identityType()).isEqualTo(MemberIdentityType.STAFFNO);
    assertThat(command.identityValue()).isEqualTo("3030609");
    assertThat(command.email()).isEqualTo("member@example.com");
    assertThat(command.language()).isEqualTo(Locale.forLanguageTag("zh-HK"));
    assertThat(command.idempotencyKey()).isEqualTo("idem_start_123");

    assertThat(response.getCookies().getFirst("auth_flow_sid"))
        .satisfies(
            cookie -> {
              assertThat(cookie.getValue()).isEqualTo("afs_opaque");
              assertThat(cookie.getPath()).isEqualTo(COOKIE_PATH);
              assertThat(cookie.getMaxAge()).isEqualTo(TTL);
              assertThat(cookie.isHttpOnly()).isTrue();
              assertThat(cookie.isSecure()).isTrue();
              assertThat(cookie.getSameSite()).isEqualTo("Strict");
            });
    assertThat(response.getCookies().getFirst("XSRF-TOKEN")).isNull();
    assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo("req_abc");
  }

  @Test
  void startMemberNotFoundReturns422WithoutAnyCookie() {
    var response = new MockServerHttpResponse();
    var controller =
        controller(
            new RecordingStartUseCase(
                Mono.just(
                    UsernameRecoveryStartResult.withoutSession(
                        UsernameRecoveryStartResult.Outcome.MEMBER_NOT_FOUND,
                        List.of()))),
            neverRequest(),
            neverCancel());
    var request =
        new UsernameRecoveryStartRequest(
            "GPN", "1234567890", "04/07/1978", "member@example.com", null, "cap_verify");

    StepVerifier.create(controller.start(request, "en", "req_1", "idem_1", response))
        .assertNext(
            entity -> {
              assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(entity.getBody().success()).isFalse();
              assertThat(entity.getBody().result().nextStep())
                  .isEqualTo(WorkflowNextStep.RESTART_FLOW);
            })
        .verifyComplete();

    assertThat(response.getCookies().getFirst("auth_flow_sid")).isNull();
    assertThat(response.getCookies().getFirst("XSRF-TOKEN")).isNull();
  }

  @Test
  void startZeroRegisteredReturns422WithoutAnyCookie() {
    var response = new MockServerHttpResponse();
    var start =
        new RecordingStartUseCase(
            Mono.just(
                UsernameRecoveryStartResult.withoutSession(
                    UsernameRecoveryStartResult.Outcome.ZERO_REGISTERED,
                    List.of(new UsernameRecoverySchemeProjection(null, "DB", "DBA", false)))));
    var controller = controller(start, neverRequest(), neverCancel());
    var request =
        new UsernameRecoveryStartRequest(
            "GPFS", "1234567890", "31/01/1980", "member@example.com", null, "cap_verify");

    StepVerifier.create(controller.start(request, "en", "req_2", "idem_2", response))
        .assertNext(
            entity -> {
              assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(entity.getBody().success()).isFalse();
              assertThat(entity.getBody().result().nextStep())
                  .isEqualTo(WorkflowNextStep.RESTART_FLOW);
            })
        .verifyComplete();

    assertThat(response.getCookies().getFirst("auth_flow_sid")).isNull();
    assertThat(response.getCookies().getFirst("XSRF-TOKEN")).isNull();
  }

  @Test
  void startMissingIdempotencyKeyRejects400WithoutInvokingUseCase() {
    var start = new RecordingStartUseCase(Mono.empty());
    var controller = controller(start, neverRequest(), neverCancel());
    var response = new MockServerHttpResponse();
    var request =
        new UsernameRecoveryStartRequest(
            "STAFF_NO", "3030609", "04/07/1978", "member@example.com", null, "cap_verify");

    StepVerifier.create(controller.start(request, "en", "req_3", null, response))
        .assertNext(entity -> assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
        .verifyComplete();

    assertThat(start.calls).isEmpty();
  }

  @Test
  void startIdempotencyInProgressReturns409WithoutAnyCookie() {
    var response = new MockServerHttpResponse();
    var start =
        new RecordingStartUseCase(
            Mono.error(UsernameRecoveryStartException.idempotencyInProgress()));
    var controller = controller(start, neverRequest(), neverCancel());
    var request =
        new UsernameRecoveryStartRequest(
            "STAFF_NO", "3030609", "04/07/1978", "member@example.com", null, "cap_verify");

    StepVerifier.create(controller.start(request, "en", "req_4", "idem_4", response))
        .assertNext(entity -> assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
        .verifyComplete();

    assertThat(response.getCookies().getFirst("auth_flow_sid")).isNull();
    assertThat(response.getCookies().getFirst("XSRF-TOKEN")).isNull();
  }

  @Test
  void startSystemFailureReturns503WithoutAnyCookie() {
    var response = new MockServerHttpResponse();
    var start =
        new RecordingStartUseCase(Mono.error(UsernameRecoveryStartException.systemFailure()));
    var controller = controller(start, neverRequest(), neverCancel());
    var request =
        new UsernameRecoveryStartRequest(
            "STAFF_NO", "3030609", "04/07/1978", "member@example.com", null, "cap_verify");

    StepVerifier.create(controller.start(request, "en", "req_5", "idem_5", response))
        .assertNext(
            entity -> assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE))
        .verifyComplete();

    assertThat(response.getCookies().getFirst("auth_flow_sid")).isNull();
    assertThat(response.getCookies().getFirst("XSRF-TOKEN")).isNull();
  }

  @Test
  void startUnexpectedRuntimeExceptionPropagatesToCentralBoundary() {
    var start = new RecordingStartUseCase(Mono.error(new RuntimeException("boom")));
    var controller = controller(start, neverRequest(), neverCancel());
    var response = new MockServerHttpResponse();
    var request =
        new UsernameRecoveryStartRequest(
            "STAFF_NO", "3030609", "04/07/1978", "member@example.com", null, "cap_verify");

    StepVerifier.create(controller.start(request, "en", "req_6", "idem_6", response))
        .expectError(RuntimeException.class)
        .verify();
  }

  @Test
  void requestInvokesRicherBoundaryWithSessionSchemeRefRawCsrfOriginRefererAndEchoesRequestId() {
    RecordingRequestUseCase requestUseCase =
        new RecordingRequestUseCase(
            Mono.just(
                new UsernameRecoveryRequestResult(
                    UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED,
                    UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED,
                    new UsernameRecoverySchemeRef("sch_1"),
                    "p****c@ah.ozk",
                    UsernameRecoveryDestinationType.EMAIL)));
    var controller = controller(neverStart(), requestUseCase, neverCancel());
    var response = new MockServerHttpResponse();

    StepVerifier.create(
            controller.request(
                "afs_opaque",
                "idem_124",
                "csrf_abc",
                "sch_1",
                "https://portal.example",
                "https://portal.example/ur",
                "en",
                "req_124",
                response))
        .assertNext(
            entity -> {
              assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
              assertThat(entity.getBody().success()).isTrue();
              assertThat(entity.getBody().result().nextStep())
                  .isEqualTo(WorkflowNextStep.SELECT_ACCOUNT);
            })
        .verifyComplete();

    assertThat(requestUseCase.calls).hasSize(1);
    RequestCall call = requestUseCase.calls.get(0);
    assertThat(call.command.authFlowSessionId()).isEqualTo(new AuthFlowSessionId("afs_opaque"));
    assertThat(call.command.schemeRef()).isEqualTo(new UsernameRecoverySchemeRef("sch_1"));
    assertThat(call.command.idempotencyKey()).isEqualTo("idem_124");
    assertThat(call.rawCsrfToken).isEqualTo("csrf_abc");
    assertThat(call.origin).isEqualTo("https://portal.example");
    assertThat(call.referer).isEqualTo("https://portal.example/ur");

    assertThat(response.getCookies()).isEmpty();
    assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo("req_124");
  }

  @Test
  void requestMissingCookieReturns401() {
    var controller = controller(neverStart(), neverRequest(), neverCancel());
    StepVerifier.create(
            controller.request(
                null,
                "idem_124",
                "csrf_abc",
                "sch_1",
                "https://portal.example",
                "https://portal.example/ur",
                "en",
                "req_124",
                new MockServerHttpResponse()))
        .assertNext(entity -> assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED))
        .verifyComplete();
  }

  @Test
  void requestMissingIdempotencyKeyReturns400() {
    var controller = controller(neverStart(), neverRequest(), neverCancel());
    StepVerifier.create(
            controller.request(
                "afs_opaque",
                null,
                "csrf_abc",
                "sch_1",
                "https://portal.example",
                "https://portal.example/ur",
                "en",
                "req_124",
                new MockServerHttpResponse()))
        .assertNext(entity -> assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
        .verifyComplete();
  }

  @Test
  void requestMissingCsrfReturns401() {
    var controller = controller(neverStart(), neverRequest(), neverCancel());
    StepVerifier.create(
            controller.request(
                "afs_opaque",
                "idem_124",
                null,
                "sch_1",
                "https://portal.example",
                "https://portal.example/ur",
                "en",
                "req_124",
                new MockServerHttpResponse()))
        .assertNext(entity -> assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED))
        .verifyComplete();
  }

  @Test
  void requestMissingSchemeRefReturns400() {
    var controller = controller(neverStart(), neverRequest(), neverCancel());
    StepVerifier.create(
            controller.request(
                "afs_opaque",
                "idem_124",
                "csrf_abc",
                null,
                "https://portal.example",
                "https://portal.example/ur",
                "en",
                "req_124",
                new MockServerHttpResponse()))
        .assertNext(entity -> assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
        .verifyComplete();
  }

  @Test
  void requestInvalidSecurityTypedExceptionReturns403WithoutResend() {
    RecordingRequestUseCase requestUseCase =
        new RecordingRequestUseCase(Mono.error(UsernameRecoveryRequestException.securityInvalid()));
    var controller = controller(neverStart(), requestUseCase, neverCancel());

    StepVerifier.create(
            controller.request(
                "afs_opaque",
                "idem_124",
                "csrf_abc",
                "sch_1",
                "https://portal.example",
                "https://portal.example/ur",
                "en",
                "req_124",
                new MockServerHttpResponse()))
        .assertNext(entity -> assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN))
        .verifyComplete();

    assertThat(requestUseCase.calls).hasSize(1);
  }

  @Test
  void requestUnknownOutcomeKeepsWaitAndInvokesUseCaseExactlyOnce() {
    RecordingRequestUseCase requestUseCase =
        new RecordingRequestUseCase(
            Mono.just(
                new UsernameRecoveryRequestResult(
                    UsernameRecoveryRequestResult.Outcome.UNKNOWN_OUTCOME,
                    null,
                    null,
                    null,
                    null)));
    var controller = controller(neverStart(), requestUseCase, neverCancel());

    StepVerifier.create(
            controller.request(
                "afs_opaque",
                "idem_124",
                "csrf_abc",
                "sch_1",
                "https://portal.example",
                "https://portal.example/ur",
                "en",
                "req_124",
                new MockServerHttpResponse()))
        .assertNext(
            entity -> {
              assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
              assertThat(entity.getBody().result().nextStep()).isEqualTo(WorkflowNextStep.WAIT);
            })
        .verifyComplete();

    assertThat(requestUseCase.calls).hasSize(1);
  }

  @Test
  void requestUnexpectedRuntimeExceptionPropagatesToCentralBoundary() {
    RecordingRequestUseCase requestUseCase =
        new RecordingRequestUseCase(Mono.error(new RuntimeException("boom")));
    var controller = controller(neverStart(), requestUseCase, neverCancel());

    StepVerifier.create(
            controller.request(
                "afs_opaque",
                "idem_124",
                "csrf_abc",
                "sch_1",
                "https://portal.example",
                "https://portal.example/ur",
                "en",
                "req_124",
                new MockServerHttpResponse()))
        .expectError(RuntimeException.class)
        .verify();
  }

  @Test
  void requestUnexpectedJavaErrorPropagatesToCentralBoundary() {
    RecordingRequestUseCase requestUseCase =
        new RecordingRequestUseCase(Mono.error(new AssertionError("boom")));
    var controller = controller(neverStart(), requestUseCase, neverCancel());

    StepVerifier.create(
            controller.request(
                "afs_opaque",
                "idem_124",
                "csrf_abc",
                "sch_1",
                "https://portal.example",
                "https://portal.example/ur",
                "en",
                "req_124",
                new MockServerHttpResponse()))
        .expectError(AssertionError.class)
        .verify();
  }

  @Test
  void cancelSuccessClearsExactPathCookiesAndInvokesRicherBoundary() {
    RecordingCancelUseCase cancelUseCase =
        new RecordingCancelUseCase(
            Mono.just(
                new UsernameRecoveryCancelResult(UsernameRecoveryCancelResult.Outcome.SUCCESS)));
    var controller = controller(neverStart(), neverRequest(), cancelUseCase);
    var response = new MockServerHttpResponse();

    StepVerifier.create(
            controller.cancel(
                "afs_opaque",
                "idem_125",
                "csrf_abc",
                "https://portal.example",
                "https://portal.example/ur",
                "en",
                "req_125",
                response))
        .assertNext(
            entity -> {
              assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
              assertThat(entity.getBody().success()).isTrue();
              assertThat(entity.getBody().result().nextStep())
                  .isEqualTo(WorkflowNextStep.RETURN_TO_LOGIN);
            })
        .verifyComplete();

    assertThat(cancelUseCase.calls).hasSize(1);
    CancelCall call = cancelUseCase.calls.get(0);
    assertThat(call.command.authFlowSessionId()).isEqualTo(new AuthFlowSessionId("afs_opaque"));
    assertThat(call.command.idempotencyKey()).isEqualTo("idem_125");
    assertThat(call.rawCsrfToken).isEqualTo("csrf_abc");
    assertThat(call.origin).isEqualTo("https://portal.example");
    assertThat(call.referer).isEqualTo("https://portal.example/ur");

    assertThat(response.getCookies().getFirst("auth_flow_sid"))
        .satisfies(
            cookie -> {
              assertThat(cookie.getValue()).isEmpty();
              assertThat(cookie.getPath()).isEqualTo(COOKIE_PATH);
              assertThat(cookie.getMaxAge().getSeconds()).isZero();
            });
    assertThat(response.getCookies().getFirst("XSRF-TOKEN")).isNull();
    assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo("req_125");
  }

  @Test
  void cancelMissingCookieReturns401() {
    var controller = controller(neverStart(), neverRequest(), neverCancel());
    StepVerifier.create(
            controller.cancel(
                null,
                "idem_125",
                "csrf_abc",
                "https://portal.example",
                "https://portal.example/ur",
                "en",
                "req_125",
                new MockServerHttpResponse()))
        .assertNext(entity -> assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED))
        .verifyComplete();
  }

  @Test
  void cancelMissingIdempotencyReturns400() {
    var controller = controller(neverStart(), neverRequest(), neverCancel());
    StepVerifier.create(
            controller.cancel(
                "afs_opaque",
                null,
                "csrf_abc",
                "https://portal.example",
                "https://portal.example/ur",
                "en",
                "req_125",
                new MockServerHttpResponse()))
        .assertNext(entity -> assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
        .verifyComplete();
  }

  @Test
  void cancelMissingCsrfReturns401() {
    var controller = controller(neverStart(), neverRequest(), neverCancel());
    StepVerifier.create(
            controller.cancel(
                "afs_opaque",
                "idem_125",
                null,
                "https://portal.example",
                "https://portal.example/ur",
                "en",
                "req_125",
                new MockServerHttpResponse()))
        .assertNext(entity -> assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED))
        .verifyComplete();
  }

  @Test
  void cancelSystemFailedReturns503WithoutClearingCookies() {
    RecordingCancelUseCase cancelUseCase =
        new RecordingCancelUseCase(
            Mono.just(
                new UsernameRecoveryCancelResult(
                    UsernameRecoveryCancelResult.Outcome.SYSTEM_FAILED)));
    var controller = controller(neverStart(), neverRequest(), cancelUseCase);
    var response = new MockServerHttpResponse();

    StepVerifier.create(
            controller.cancel(
                "afs_opaque",
                "idem_125",
                "csrf_abc",
                "https://portal.example",
                "https://portal.example/ur",
                "en",
                "req_125",
                response))
        .assertNext(
            entity -> assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE))
        .verifyComplete();

    assertThat(response.getCookies()).isEmpty();
  }

  @Test
  void cancelSecurityInvalidReturns403WithoutClearingCookies() {
    RecordingCancelUseCase cancelUseCase =
        new RecordingCancelUseCase(Mono.error(UsernameRecoveryCancelException.securityInvalid()));
    var controller = controller(neverStart(), neverRequest(), cancelUseCase);
    var response = new MockServerHttpResponse();

    StepVerifier.create(
            controller.cancel(
                "afs_opaque",
                "idem_125",
                "csrf_abc",
                "https://portal.example",
                "https://portal.example/ur",
                "en",
                "req_125",
                response))
        .assertNext(entity -> assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN))
        .verifyComplete();

    assertThat(response.getCookies()).isEmpty();
  }

  @Test
  void startNullBodyReturnsApprovedStartValidationResponseWithoutInvokingUseCase() {
    var controller = controller(neverStart(), neverRequest(), neverCancel());
    var response = new MockServerHttpResponse();

    StepVerifier.create(controller.start(null, "en", "req_7", "idem_7", response))
        .assertNext(
            entity -> {
              assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(entity.getBody().success()).isFalse();
              assertThat(entity.getBody().result().nextStep()).isEqualTo(WorkflowNextStep.NONE);
              assertThat(entity.getBody().errors())
                  .singleElement()
                  .extracting(error -> error.code())
                  .isEqualTo("err.request.validation.failed");
            })
        .verifyComplete();

    assertThat(response.getCookies()).isEmpty();
  }

  @Test
  void startUnsupportedIdentityTypeReturnsApprovedStartValidationResponseWithoutInvokingUseCase() {
    var controller = controller(neverStart(), neverRequest(), neverCancel());
    var response = new MockServerHttpResponse();
    var request =
        new UsernameRecoveryStartRequest(
            "BOGUS", "3030609", "04/07/1978", "member@example.com", null, "cap_verify");

    StepVerifier.create(controller.start(request, "en", "req_8", "idem_8", response))
        .assertNext(
            entity -> {
              assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(entity.getBody().success()).isFalse();
              assertThat(entity.getBody().result().nextStep()).isEqualTo(WorkflowNextStep.NONE);
              assertThat(entity.getBody().errors())
                  .singleElement()
                  .extracting(error -> error.code())
                  .isEqualTo("err.request.validation.failed");
            })
        .verifyComplete();

    assertThat(response.getCookies()).isEmpty();
  }

  @Test
  void requestMalformedSessionIdReturnsApprovedSessionRestartResponseWithoutInvokingUseCase() {
    var controller = controller(neverStart(), neverRequest(), neverCancel());

    StepVerifier.create(
            controller.request(
                "bad session id!",
                "idem_124",
                "csrf_abc",
                "sch_1",
                "https://portal.example",
                "https://portal.example/ur",
                "en",
                "req_124",
                new MockServerHttpResponse()))
        .assertNext(
            entity -> {
              assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(entity.getBody().result().nextStep())
                  .isEqualTo(WorkflowNextStep.RESTART_FLOW);
              assertThat(entity.getBody().errors())
                  .singleElement()
                  .extracting(error -> error.code())
                  .isEqualTo("err.usernamerecovery.authFlow.invalid");
            })
        .verifyComplete();
  }

  @Test
  void requestMalformedNonblankSchemeRefReturnsInvalidSchemeRefResponseWithoutInvokingUseCase() {
    var controller = controller(neverStart(), neverRequest(), neverCancel());
    // A value that is nonblank per String#isBlank (so the missing-Scheme-Ref
    // helper is not chosen) but that trims to blank; the opaque Scheme-Ref
    // conversion rejects it with IllegalArgumentException at the boundary.
    String malformedSchemeRef = "\u0001 ";

    StepVerifier.create(
            controller.request(
                "afs_opaque",
                "idem_124",
                "csrf_abc",
                malformedSchemeRef,
                "https://portal.example",
                "https://portal.example/ur",
                "en",
                "req_124",
                new MockServerHttpResponse()))
        .assertNext(
            entity -> {
              assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(entity.getBody().result().nextStep())
                  .isEqualTo(WorkflowNextStep.SELECT_ACCOUNT);
              assertThat(entity.getBody().errors())
                  .singleElement()
                  .extracting(error -> error.code())
                  .isEqualTo("err.usernamerecovery.schemeRef.invalid");
            })
        .verifyComplete();
  }

  @Test
  void cancelMalformedSessionIdReturnsApprovedCancelValidationResponseWithoutInvokingUseCase() {
    var controller = controller(neverStart(), neverRequest(), neverCancel());

    StepVerifier.create(
            controller.cancel(
                "bad session id!",
                "idem_125",
                "csrf_abc",
                "https://portal.example",
                "https://portal.example/ur",
                "en",
                "req_125",
                new MockServerHttpResponse()))
        .assertNext(
            entity -> {
              assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(entity.getBody().result().nextStep())
                  .isEqualTo(WorkflowNextStep.RETURN_TO_LOGIN);
              assertThat(entity.getBody().errors())
                  .singleElement()
                  .extracting(error -> error.code())
                  .isEqualTo("err.request.validation.failed");
            })
        .verifyComplete();
  }

  private static UsernameRecoveryStartResult sessionCreated(
      String sessionId, String csrfToken, String schemeRef) {
    return new UsernameRecoveryStartResult(
        UsernameRecoveryStartResult.Outcome.SESSION_CREATED,
        new AuthFlowSessionId(sessionId),
        csrfToken,
        600,
        List.of(
            new UsernameRecoverySchemeProjection(
                new UsernameRecoverySchemeRef(schemeRef), "DB", "DBA", true)));
  }

  private static RequestUsernameRecoveryUseCase neverRequest() {
    return (command, csrf, origin, referer) ->
        Mono.error(new AssertionError("request must not be called"));
  }

  private static CancelUsernameRecoveryUseCase neverCancel() {
    return (command, csrf, origin, referer) ->
        Mono.error(new AssertionError("cancel must not be called"));
  }

  private static StartUsernameRecoveryUseCase neverStart() {
    return command -> Mono.error(new AssertionError("start must not be called"));
  }

  private static UsernameRecoveryController controller(
      StartUsernameRecoveryUseCase start,
      RequestUsernameRecoveryUseCase request,
      CancelUsernameRecoveryUseCase cancel) {
    SessionCookiePolicy authFlowPolicy =
        new SessionCookiePolicy(
            "auth_flow_sid",
            COOKIE_PATH,
            (int) TTL.getSeconds(),
            true,
            true,
            SessionCookieSameSite.STRICT);
    return new UsernameRecoveryController(
        start,
        new UsernameRecoveryStartWebMapper(),
        new UsernameRecoveryStartResponsePolicy(),
        request,
        new UsernameRecoveryRequestWebMapper(),
        new UsernameRecoveryRequestResponsePolicy(),
        cancel,
        new UsernameRecoveryCancelWebMapper(),
        new UsernameRecoveryCancelResponsePolicy(),
        new UsernameRecoveryCookieWriter(authFlowPolicy),
        new HttpSessionCookieAdapter());
  }

  private static final class RecordingStartUseCase implements StartUsernameRecoveryUseCase {
    final List<UsernameRecoveryStartCommand> calls = new ArrayList<>();
    private final Mono<UsernameRecoveryStartResult> result;

    RecordingStartUseCase(Mono<UsernameRecoveryStartResult> result) {
      this.result = result;
    }

    @Override
    public Mono<UsernameRecoveryStartResult> start(UsernameRecoveryStartCommand command) {
      calls.add(command);
      return result;
    }
  }

  private static final class RecordingRequestUseCase implements RequestUsernameRecoveryUseCase {
    final List<RequestCall> calls = new ArrayList<>();
    private final Mono<UsernameRecoveryRequestResult> result;

    RecordingRequestUseCase(Mono<UsernameRecoveryRequestResult> result) {
      this.result = result;
    }

    @Override
    public Mono<UsernameRecoveryRequestResult> request(
        UsernameRecoveryRequestCommand command,
        String rawCsrfToken,
        String origin,
        String referer) {
      calls.add(new RequestCall(command, rawCsrfToken, origin, referer));
      return result;
    }
  }

  private record RequestCall(
      UsernameRecoveryRequestCommand command, String rawCsrfToken, String origin, String referer) {}

  private static final class RecordingCancelUseCase implements CancelUsernameRecoveryUseCase {
    final List<CancelCall> calls = new ArrayList<>();
    private final Mono<UsernameRecoveryCancelResult> result;

    RecordingCancelUseCase(Mono<UsernameRecoveryCancelResult> result) {
      this.result = result;
    }

    @Override
    public Mono<UsernameRecoveryCancelResult> cancel(
        UsernameRecoveryCancelCommand command, String rawCsrfToken, String origin, String referer) {
      calls.add(new CancelCall(command, rawCsrfToken, origin, referer));
      return result;
    }
  }

  private record CancelCall(
      UsernameRecoveryCancelCommand command, String rawCsrfToken, String origin, String referer) {}
}
