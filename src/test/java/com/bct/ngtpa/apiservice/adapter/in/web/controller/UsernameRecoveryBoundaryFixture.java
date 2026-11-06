package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.mapper.UsernameRecoveryCancelWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.mapper.UsernameRecoveryRequestWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.mapper.UsernameRecoveryStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.policy.UsernameRecoveryCancelResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.policy.UsernameRecoveryRequestResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.policy.UsernameRecoveryStartResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.session.cookie.HttpSessionCookieAdapter;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.TrpSearchMemberAdapter;
import com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.dto.TrpSearchMemberApimDataItem;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestResult;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookiePolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieSameSite;
import com.bct.ngtpa.apiservice.application.port.in.CancelUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.RequestUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.StartUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.out.MessageDeliveryPort;
import com.bct.ngtpa.apiservice.application.port.out.NoticeTemplateRegistryPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryEffectiveDestinationPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryRequestProtectedOperation;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartOperationHarness;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartValidator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.Sha256CsrfTokenHasher;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import java.util.List;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/**
 * Boundary fixture: real TrpSearchMemberAdapter + mapper, real Start/Request protected operations,
 * real controller/mappers/policies/cookie writer. Only the APIM executor, the captcha gate, the
 * auth-flow session store, the raw CSRF source and the delivery-side ports are doubled.
 */
final class UsernameRecoveryBoundaryFixture {
  static final String START_PATH = "/api/v1/auth/username-recovery/start";
  static final String COOKIE_PATH = "/api/v1/auth/username-recovery";

  final ApimExchangeExecutor apim = mock(ApimExchangeExecutor.class);
  final UsernameRecoveryStartOperationHarness harness =
      new UsernameRecoveryStartOperationHarness(new TrpSearchMemberAdapter(apim));
  final MessageDeliveryPort delivery = mock(MessageDeliveryPort.class);
  final UsernameRecoveryEffectiveDestinationPort destinations =
      mock(UsernameRecoveryEffectiveDestinationPort.class);
  final NoticeTemplateRegistryPort templates = mock(NoticeTemplateRegistryPort.class);
  final UsernameRecoveryRequestProtectedOperation requestOperation =
      new UsernameRecoveryRequestProtectedOperation(
          harness.binding,
          new CsrfTokenValidationService(new Sha256CsrfTokenHasher()),
          destinations,
          harness.sessions,
          delivery,
          new MaskingService(new MaskingProperties()),
          List.of(),
          templates);
  final UsernameRecoveryController controller = controller();
  final WebTestClient client = WebTestClient.bindToController(controller).build();

  void givenApimReturns(String errMessage, List<TrpSearchMemberApimDataItem> data) {
    when(apim.postEnvelope(any()))
        .thenAnswer(
            invocation ->
                Mono.just(new ApimResponseEnvelope<>(new ApimResponseBody<>(errMessage, data))));
  }

  private UsernameRecoveryController controller() {
    var validator = new UsernameRecoveryStartValidator();
    StartUsernameRecoveryUseCase start =
        command -> harness.operation.execute(command, validator.validate(command));
    RequestUsernameRecoveryUseCase request =
        (command, csrf, origin, referer) ->
            requestOperation
                .execute(
                    command,
                    csrf,
                    origin,
                    referer,
                    "corr-boundary",
                    UsernameRecoveryStartOperationHarness.NOW)
                .flatMap(
                    protectedResult -> {
                      if (protectedResult.completed()) {
                        return Mono.just(protectedResult.result());
                      }
                      return Mono.<UsernameRecoveryRequestResult>error(protectedResult.failure());
                    });
    CancelUsernameRecoveryUseCase cancel =
        (command, csrf, origin, referer) ->
            Mono.error(new AssertionError("Cancel use case must not run"));
    // Mirrors the production auth_flow_sid policy pinned in UsernameRecoveryFeatureConfigTest
    // (SameSite NONE).
    SessionCookiePolicy authFlow =
        new SessionCookiePolicy(
            "auth_flow_sid", COOKIE_PATH, 600, true, true, SessionCookieSameSite.NONE);
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
        new UsernameRecoveryCookieWriter(authFlow),
        new HttpSessionCookieAdapter());
  }

  static TrpSearchMemberApimDataItem unregisteredItem(String env, String trustCode, String name) {
    return new TrpSearchMemberApimDataItem(
        env, trustCode, "OE", name, null, "00000008802", "5", "", "", false, null);
  }

  static TrpSearchMemberApimDataItem registeredItem(
      String env, String trustCode, String name, String custId) {
    return new TrpSearchMemberApimDataItem(
        env, trustCode, "OE", name, null, "00000008802", "5", "", "", true, custId);
  }

  static String validRequest() {
    return """
        {
          "identityType": "STAFF_NO",
          "identityValue": "3030609",
          "dateOfBirth": "04/07/1978",
          "email": "member@example.com",
          "mobile": null,
          "captchaChallengeId": "%s"
        }
        """
        .formatted(UsernameRecoveryStartOperationHarness.CAPTCHA);
  }
}
