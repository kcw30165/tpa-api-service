package com.bct.ngtpa.apiservice.adapter.out.apim.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimPayloadCryptoService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimWebClientFacade;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfile;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.security.PublicKey;
import java.util.List;
import java.util.function.BiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DefaultApimExchangeExecutorTest {
  private static final String PATH = "/ws/NGTPA/v1/Test";
  private static final RequestDto SOURCE = new RequestDto("value");
  private static final RequestDto TRANSFORMED = new RequestDto("cipher");
  private static final ApimCredentialResolutionContext CONTEXT =
      new ApimCredentialResolutionContext(ActorUserRole.ROLE_USER_MBR, "DEV");
  private static final ApimCredentialProfile PROFILE =
      new ApimCredentialProfile(
          "p1",
          "client",
          "secret",
          "api-key",
          "https://token",
          "https://apim",
          List.of(),
          "/certificate");
  private final ApimWebClientFacade facade = mock(ApimWebClientFacade.class);
  private final ApimPayloadCryptoService crypto = mock(ApimPayloadCryptoService.class);
  private final ApimProperties properties = new ApimProperties();
  private DefaultApimExchangeExecutor executor;
  private ApimExchangeRequest<RequestDto, ResponseDto> request;

  @BeforeEach
  void setUp() {
    executor = new DefaultApimExchangeExecutor(facade, crypto, properties);
    request =
        new ApimExchangeRequest<>(
            PATH,
            SOURCE,
            RequestDto.class,
            ResponseDto.class,
            ApimDatabaseScope.ACCOUNT_ENV,
            CONTEXT,
            ApimRetryEligibility.SAFE_BUSINESS_CALL);
  }

  @Test
  void plainModeUsesNullKeysResolvesProfileAndReturnsTypedEnvelope() {
    properties.getEncryption().setEnabled(false);
    ApimResponseEnvelope<ResponseDto> envelope = mock(ApimResponseEnvelope.class);
    when(crypto.encryptRequest(PATH, SOURCE, RequestDto.class, null)).thenReturn(SOURCE);
    when(facade.postSuccessfulExchange(
            PATH,
            SOURCE,
            ApimDatabaseScope.ACCOUNT_ENV,
            CONTEXT,
            ApimRetryEligibility.SAFE_BUSINESS_CALL, ApimResubmissionPolicy.ALLOW_RECOVERY, ApimIncidentOwnership.APIM_OBSERVER))
        .thenReturn(Mono.just(new ApimSuccessfulExchange("body", PROFILE, null)));
    when(crypto.decryptResponseEnvelope(PATH, "body", ResponseDto.class, null))
        .thenReturn(envelope);
    assertThat(executor.postEnvelope(request).block()).isSameAs(envelope);
    verify(crypto).encryptRequest(PATH, SOURCE, RequestDto.class, null);
    verify(crypto).decryptResponseEnvelope(PATH, "body", ResponseDto.class, null);
  }

  @Test
  void encryptedModeDelegatesGenerationAndDecryptsWithExactSuccessfulAttemptKey() {
    properties.getEncryption().setEnabled(true);
    PublicKey attemptKey = mock(PublicKey.class);
    PublicKey successfulKey = mock(PublicKey.class);
    ApimResponseEnvelope<ResponseDto> envelope = mock(ApimResponseEnvelope.class);
    ArgumentCaptor<BiFunction<ApimCredentialProfile, PublicKey, Object>> fn =
        ArgumentCaptor.forClass(BiFunction.class);
    when(facade.postEncryptedSuccessfulExchange(
            eq(PATH),
            fn.capture(),
            eq(ApimDatabaseScope.ACCOUNT_ENV),
            eq(CONTEXT),
            eq(ApimRetryEligibility.SAFE_BUSINESS_CALL), any(ApimResubmissionPolicy.class), any(ApimIncidentOwnership.class)))
        .thenReturn(Mono.just(new ApimSuccessfulExchange("body", PROFILE, successfulKey)));
    when(crypto.encryptRequest(PATH, SOURCE, RequestDto.class, attemptKey)).thenReturn(TRANSFORMED);
    when(crypto.decryptResponseEnvelope(PATH, "body", ResponseDto.class, successfulKey))
        .thenReturn(envelope);
    assertThat(executor.postEnvelope(request).block()).isSameAs(envelope);
    assertThat(fn.getValue().apply(PROFILE, attemptKey)).isSameAs(TRANSFORMED);
    verify(crypto).decryptResponseEnvelope(PATH, "body", ResponseDto.class, successfulKey);
  }

  @Test
  void refreshedSuccessfulKeyIsAuthoritativeForResponseDecryption() {
    properties.getEncryption().setEnabled(true);
    PublicKey refreshed = mock(PublicKey.class);
    ApimResponseEnvelope<ResponseDto> envelope = mock(ApimResponseEnvelope.class);
    when(facade.postEncryptedSuccessfulExchange(
            eq(PATH),
            any(),
            eq(ApimDatabaseScope.ACCOUNT_ENV),
            eq(CONTEXT),
            eq(ApimRetryEligibility.SAFE_BUSINESS_CALL), any(ApimResubmissionPolicy.class), any(ApimIncidentOwnership.class)))
        .thenReturn(Mono.just(new ApimSuccessfulExchange("body", PROFILE, refreshed)));
    when(crypto.decryptResponseEnvelope(PATH, "body", ResponseDto.class, refreshed))
        .thenReturn(envelope);
    assertThat(executor.postEnvelope(request).block()).isSameAs(envelope);
  }

  @Test
  void callerRoutingContextAndRetryPassThroughUnchanged() {
    properties.getEncryption().setEnabled(false);
    when(crypto.encryptRequest(PATH, SOURCE, RequestDto.class, null)).thenReturn(SOURCE);
    when(facade.postSuccessfulExchange(
            PATH,
            SOURCE,
            ApimDatabaseScope.ACCOUNT_ENV,
            CONTEXT,
            ApimRetryEligibility.SAFE_BUSINESS_CALL, ApimResubmissionPolicy.ALLOW_RECOVERY, ApimIncidentOwnership.APIM_OBSERVER))
        .thenReturn(Mono.just(new ApimSuccessfulExchange("body", PROFILE, null)));
    when(crypto.decryptResponseEnvelope(eq(PATH), eq("body"), eq(ResponseDto.class), eq(null)))
        .thenReturn(mock(ApimResponseEnvelope.class));
    executor.postEnvelope(request).block();
    verify(facade)
        .postSuccessfulExchange(
            PATH,
            SOURCE,
            ApimDatabaseScope.ACCOUNT_ENV,
            CONTEXT,
            ApimRetryEligibility.SAFE_BUSINESS_CALL,
            ApimResubmissionPolicy.ALLOW_RECOVERY,
            ApimIncidentOwnership.APIM_OBSERVER);
  }

  @Test
  void synchronousRequestCryptoFailureIsReactive() {
    properties.getEncryption().setEnabled(false);
    when(crypto.encryptRequest(PATH, SOURCE, RequestDto.class, null))
        .thenThrow(new IllegalStateException("request crypto"));
    Mono<ApimResponseEnvelope<ResponseDto>> result = executor.postEnvelope(request);
    StepVerifier.create(result).expectErrorMessage("request crypto").verify();
  }

  @Test
  void synchronousResponseCryptoFailureIsReactive() {
    properties.getEncryption().setEnabled(false);
    when(crypto.encryptRequest(PATH, SOURCE, RequestDto.class, null)).thenReturn(SOURCE);
    when(facade.postSuccessfulExchange(anyString(), any(), any(), any(), any(), any(ApimResubmissionPolicy.class), any(ApimIncidentOwnership.class)))
        .thenReturn(Mono.just(new ApimSuccessfulExchange("body", PROFILE, null)));
    when(crypto.decryptResponseEnvelope(PATH, "body", ResponseDto.class, null))
        .thenThrow(new IllegalStateException("response crypto"));
    StepVerifier.create(executor.postEnvelope(request))
        .expectErrorMessage("response crypto")
        .verify();
  }

  @Test
  void emptySuccessfulExchangeFailsDeterministically() {
    properties.getEncryption().setEnabled(false);
    when(crypto.encryptRequest(PATH, SOURCE, RequestDto.class, null)).thenReturn(SOURCE);
    when(facade.postSuccessfulExchange(anyString(), any(), any(), any(), any(), any(ApimResubmissionPolicy.class), any(ApimIncidentOwnership.class)))
        .thenReturn(Mono.empty());
    StepVerifier.create(executor.postEnvelope(request))
        .expectErrorMessage("APIM successful exchange was empty")
        .verify();
  }

  @Test
  void encryptedNullKeyFailsDeterministically() {
    properties.getEncryption().setEnabled(true);
    when(facade.postEncryptedSuccessfulExchange(anyString(), any(), any(), any(), any(), any(ApimResubmissionPolicy.class), any(ApimIncidentOwnership.class)))
        .thenReturn(Mono.just(new ApimSuccessfulExchange("body", PROFILE, null)));
    StepVerifier.create(executor.postEnvelope(request))
        .expectErrorMessage("Encrypted APIM exchange missing verification key")
        .verify();
  }

  @Test
  void plainNonnullKeyFailsDeterministically() {
    properties.getEncryption().setEnabled(false);
    PublicKey key = mock(PublicKey.class);
    when(crypto.encryptRequest(PATH, SOURCE, RequestDto.class, null)).thenReturn(SOURCE);
    when(facade.postSuccessfulExchange(anyString(), any(), any(), any(), any(), any(ApimResubmissionPolicy.class), any(ApimIncidentOwnership.class)))
        .thenReturn(Mono.just(new ApimSuccessfulExchange("body", PROFILE, key)));
    StepVerifier.create(executor.postEnvelope(request))
        .expectErrorMessage("Plain APIM exchange unexpectedly returned verification key")
        .verify();
  }

  @Test
  void executorDoesNotApplyBusinessEnvelopeValidation() {
    properties.getEncryption().setEnabled(false);
    ApimResponseEnvelope<ResponseDto> businessFailureEnvelope = mock(ApimResponseEnvelope.class);
    when(crypto.encryptRequest(PATH, SOURCE, RequestDto.class, null)).thenReturn(SOURCE);
    when(facade.postSuccessfulExchange(anyString(), any(), any(), any(), any(), any(ApimResubmissionPolicy.class), any(ApimIncidentOwnership.class)))
        .thenReturn(Mono.just(new ApimSuccessfulExchange("business", PROFILE, null)));
    when(crypto.decryptResponseEnvelope(PATH, "business", ResponseDto.class, null))
        .thenReturn(businessFailureEnvelope);
    assertThat(executor.postEnvelope(request).block()).isSameAs(businessFailureEnvelope);
  }

  private record RequestDto(String value) {}

  private record ResponseDto(String value) {}
  @Test
  void terminalCallerPropagatesForPlainAllowRecovery() {
    assertPlainOwnership(ApimResubmissionPolicy.ALLOW_RECOVERY, ApimRetryEligibility.SAFE_BUSINESS_CALL);
  }

  @Test
  void terminalCallerPropagatesForPlainNever() {
    assertPlainOwnership(ApimResubmissionPolicy.NEVER, ApimRetryEligibility.NON_RETRYABLE);
  }

  @Test
  void terminalCallerPropagatesForEncryptedAllowRecovery() {
    assertEncryptedOwnership(ApimResubmissionPolicy.ALLOW_RECOVERY, ApimRetryEligibility.SAFE_BUSINESS_CALL);
  }

  @Test
  void terminalCallerPropagatesForEncryptedNever() {
    assertEncryptedOwnership(ApimResubmissionPolicy.NEVER, ApimRetryEligibility.NON_RETRYABLE);
  }

  private void assertPlainOwnership(
      ApimResubmissionPolicy policy, ApimRetryEligibility eligibility) {
    properties.getEncryption().setEnabled(false);
    var terminal = new ApimExchangeRequest<>(PATH,SOURCE,RequestDto.class,ResponseDto.class,
        ApimDatabaseScope.ACCOUNT_ENV,CONTEXT,eligibility,policy,ApimIncidentOwnership.TERMINAL_CALLER);
    when(crypto.encryptRequest(PATH,SOURCE,RequestDto.class,null)).thenReturn(SOURCE);
    when(facade.postSuccessfulExchange(PATH,SOURCE,ApimDatabaseScope.ACCOUNT_ENV,CONTEXT,
        eligibility,policy,ApimIncidentOwnership.TERMINAL_CALLER))
        .thenReturn(Mono.error(new IllegalStateException("terminal-stop")));
    StepVerifier.create(executor.postEnvelope(terminal)).expectErrorMessage("terminal-stop").verify();
    verify(facade).postSuccessfulExchange(PATH,SOURCE,ApimDatabaseScope.ACCOUNT_ENV,CONTEXT,
        eligibility,policy,ApimIncidentOwnership.TERMINAL_CALLER);
  }

  private void assertEncryptedOwnership(
      ApimResubmissionPolicy policy, ApimRetryEligibility eligibility) {
    properties.getEncryption().setEnabled(true);
    var terminal = new ApimExchangeRequest<>(PATH,SOURCE,RequestDto.class,ResponseDto.class,
        ApimDatabaseScope.ACCOUNT_ENV,CONTEXT,eligibility,policy,ApimIncidentOwnership.TERMINAL_CALLER);
    when(facade.postEncryptedSuccessfulExchange(eq(PATH),any(),eq(ApimDatabaseScope.ACCOUNT_ENV),
        eq(CONTEXT),eq(eligibility),eq(policy),eq(ApimIncidentOwnership.TERMINAL_CALLER)))
        .thenReturn(Mono.error(new IllegalStateException("terminal-stop")));
    StepVerifier.create(executor.postEnvelope(terminal)).expectErrorMessage("terminal-stop").verify();
    verify(facade).postEncryptedSuccessfulExchange(eq(PATH),any(),eq(ApimDatabaseScope.ACCOUNT_ENV),
        eq(CONTEXT),eq(eligibility),eq(policy),eq(ApimIncidentOwnership.TERMINAL_CALLER));
  }

}
