package com.bct.ngtpa.apiservice.adapter.out.apim.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimPayloadCryptoService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimWebClientFacade;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfile;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

/**
 * Verifies that outcome logging is invoked as a pure side effect and never alters the envelope or
 * exception that flows back to the calling adapter. This is a companion to the pre-existing
 * behavioral tests in {@code DefaultApimExchangeExecutorTest}, which are intentionally left
 * unmodified (they already prove plain/encrypted transport behavior and continue to construct the
 * executor with the retained legacy 3-arg constructor).
 */
class DefaultApimExchangeExecutorOutcomeLoggingTest {

  private static final String PATH = "/ws/NGTPA/v1/Test";
  private static final ApimCredentialResolutionContext CONTEXT =
      new ApimCredentialResolutionContext(ActorUserRole.ROLE_USER_MBR, "DEV");

  private final ApimWebClientFacade facade = mock(ApimWebClientFacade.class);
  private final ApimPayloadCryptoService crypto = mock(ApimPayloadCryptoService.class);
  private final ApimProperties properties = new ApimProperties();
  private final ApimExchangeOutcomeLogger outcomeLogger = mock(ApimExchangeOutcomeLogger.class);
  private DefaultApimExchangeExecutor executor;
  private ApimExchangeRequest<String, String> request;

  @BeforeEach
  void setUp() {
    properties.getEncryption().setEnabled(false);
    executor = new DefaultApimExchangeExecutor(facade, crypto, properties, outcomeLogger);
    request =
        new ApimExchangeRequest<>(
            PATH,
            "source",
            String.class,
            String.class,
            ApimDatabaseScope.ACCOUNT_ENV,
            CONTEXT,
            ApimRetryEligibility.SAFE_BUSINESS_CALL);
    when(crypto.encryptRequest(PATH, "source", String.class, null)).thenReturn("source");
  }

  @Test
  void successEmissionInvokesSuccessLoggingWithUnalteredEnvelope() {
    ApimResponseEnvelope<String> envelope =
        new ApimResponseEnvelope<>(new ApimResponseBody<>("", List.of("x")));
    when(facade.postSuccessfulExchange(
            anyString(),
            any(),
            any(),
            any(),
            any(),
            any(ApimResubmissionPolicy.class),
            any(ApimIncidentOwnership.class)))
        .thenReturn(Mono.just(new ApimSuccessfulExchange("body", profile(), null)));
    when(crypto.decryptResponseEnvelope(PATH, "body", String.class, null)).thenReturn(envelope);

    ApimResponseEnvelope<String> result = executor.postEnvelope(request).block();

    assertThat(result).isSameAs(envelope);
    verify(outcomeLogger).logSuccessOutcome(eq(request), eq(envelope), isNull());
  }

  @Test
  void errorSignalInvokesFailureLoggingAndPropagatesOriginalException() {
    RuntimeException boom = new RuntimeException("boom");
    when(crypto.encryptRequest(PATH, "source", String.class, null)).thenThrow(boom);

    StepVerifier.create(executor.postEnvelope(request)).expectErrorMessage("boom").verify();

    verify(outcomeLogger).logExchangeFailure(eq(request), eq(boom), isNull());
  }

  @Test
  void requestIdFromReactorContextIsForwardedToOutcomeLogger() {
    ApimResponseEnvelope<String> envelope =
        new ApimResponseEnvelope<>(new ApimResponseBody<>("", List.of()));
    when(facade.postSuccessfulExchange(
            anyString(),
            any(),
            any(),
            any(),
            any(),
            any(ApimResubmissionPolicy.class),
            any(ApimIncidentOwnership.class)))
        .thenReturn(Mono.just(new ApimSuccessfulExchange("body", profile(), null)));
    when(crypto.decryptResponseEnvelope(PATH, "body", String.class, null)).thenReturn(envelope);

    executor
        .postEnvelope(request)
        .contextWrite(Context.of(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, "req-42"))
        .block();

    verify(outcomeLogger).logSuccessOutcome(eq(request), eq(envelope), eq("req-42"));
  }

  @Test
  void malformedOrRejectedEnvelopeStillFlowsThroughUnalteredToCaller() {
    ApimResponseEnvelope<String> businessFailureEnvelope =
        new ApimResponseEnvelope<>(new ApimResponseBody<>("err.tpa.wrong.password", null));
    when(facade.postSuccessfulExchange(
            anyString(),
            any(),
            any(),
            any(),
            any(),
            any(ApimResubmissionPolicy.class),
            any(ApimIncidentOwnership.class)))
        .thenReturn(Mono.just(new ApimSuccessfulExchange("body", profile(), null)));
    when(crypto.decryptResponseEnvelope(PATH, "body", String.class, null))
        .thenReturn(businessFailureEnvelope);

    ApimResponseEnvelope<String> result = executor.postEnvelope(request).block();

    assertThat(result).isSameAs(businessFailureEnvelope);
    verify(outcomeLogger, times(1))
        .logSuccessOutcome(eq(request), eq(businessFailureEnvelope), isNull());
  }

  private ApimCredentialProfile profile() {
    return new ApimCredentialProfile(
        "p1",
        "client",
        "secret",
        "api-key",
        "https://token",
        "https://apim",
        List.of(),
        "/certificate");
  }
}
