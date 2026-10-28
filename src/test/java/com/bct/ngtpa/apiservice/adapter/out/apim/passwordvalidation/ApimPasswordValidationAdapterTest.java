package com.bct.ngtpa.apiservice.adapter.out.apim.passwordvalidation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimResubmissionPolicy;
import com.bct.ngtpa.apiservice.adapter.out.apim.passwordvalidation.dto.TrpPwdValidationDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.passwordvalidation.dto.TrpPwdValidationRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordCheckCommand;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordCheckResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ApimPasswordValidationAdapterTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final ApimExchangeExecutor executor = mock(ApimExchangeExecutor.class);
  private final ApimPasswordValidationAdapter adapter = new ApimPasswordValidationAdapter(executor);
  private final PasswordCheckCommand command =
      new PasswordCheckCommand("test-customer", " AbC ", ActorUserRole.ROLE_INPUT);

  @Test
  void exactRequestHasOnlyServerIdentityAndUnchangedPasswordWithStrictRouting() throws Exception {
    stub("{\"response\":{\"err-message\":\"\",\"data\":[{\"success\":true,\"fail-count\":0}]}}");
    StepVerifier.create(adapter.check(command)).expectNext(PasswordCheckResult.CORRECT).verifyComplete();
    var capture = ArgumentCaptor.forClass(ApimExchangeRequest.class);
    verify(executor).postEnvelope(capture.capture());
    var request = capture.getValue();
    assertThat(request.path()).isEqualTo("/ws/NGTPA/v1/TRPPwdValidation");
    assertThat(request.requestType()).isEqualTo(TrpPwdValidationRequest.class);
    assertThat(request.responseDataType()).isEqualTo(TrpPwdValidationDataItem.class);
    assertThat(request.databaseScope()).isEqualTo(ApimDatabaseScope.NON_ACCOUNT);
    assertThat(request.credentialContext().accountEnv()).isNull();
    assertThat(request.credentialContext().actorUserRole()).isEqualTo(ActorUserRole.ROLE_INPUT);
    assertThat(request.retryEligibility()).isEqualTo(ApimRetryEligibility.NON_RETRYABLE);
    assertThat(request.resubmissionPolicy()).isEqualTo(ApimResubmissionPolicy.NEVER);
    assertThat(mapper.readTree(mapper.writeValueAsString(request.request())))
        .isEqualTo(mapper.readTree("{\"cust-id\":\"test-customer\",\"password\":\" AbC \"}"));
    assertThat(request.request().toString()).contains("customerId=<redacted>", "password=<redacted>")
        .doesNotContain("test-customer", " AbC ");
  }

  @ParameterizedTest
  @CsvSource({
      "err.tpa.account.locked,ACCOUNT_LOCKED",
      "err.tpa.cust.id.not.registered,CUSTOMER_NOT_REGISTERED",
      "err.tpa.unknown,UNAVAILABLE", "' ',UNAVAILABLE",
      "' err.tpa.account.locked',UNAVAILABLE", "ERR.TPA.ACCOUNT.LOCKED,UNAVAILABLE"
  })
  void exactErrorsAreAuthoritativeWithoutRequiringData(String error, PasswordCheckResult expected)
      throws Exception {
    stub("{\"response\":{\"err-message\":" + mapper.writeValueAsString(error) + "}}");
    StepVerifier.create(adapter.check(command)).expectNext(expected).verifyComplete();
  }

  @Test
  void recognizedErrorNeverBecomesSuccessEvenWithContradictoryData() throws Exception {
    stub("{\"response\":{\"err-message\":\"err.tpa.account.locked\",\"data\":[{\"success\":true}]}}");
    StepVerifier.create(adapter.check(command)).expectNext(PasswordCheckResult.ACCOUNT_LOCKED).verifyComplete();
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "5", "99", "null", "\"unknown\"", "{}", "[]"})
  void failCountNeverChangesIncorrectPasswordIntoLock(String count) throws Exception {
    stub("{\"response\":{\"err-message\":\"\",\"data\":[{\"success\":false,\"fail-count\":" + count + "}]}}");
    StepVerifier.create(adapter.check(command)).expectNext(PasswordCheckResult.INCORRECT).verifyComplete();
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "{}", "{\"response\":null}", "{\"response\":{}}",
      "{\"response\":{\"err-message\":null}}",
      "{\"response\":{\"err-message\":\"\",\"data\":null}}",
      "{\"response\":{\"err-message\":\"\",\"data\":[]}}",
      "{\"response\":{\"err-message\":\"\",\"data\":[null]}}",
      "{\"response\":{\"err-message\":\"\",\"data\":[{}]}}",
      "{\"response\":{\"err-message\":\"\",\"data\":[{\"success\":null}]}}",
      "{\"response\":{\"err-message\":\"\",\"data\":[{\"success\":\"true\"}]}}",
      "{\"response\":{\"err-message\":\"\",\"data\":[{\"success\":1}]}}",
      "{\"response\":{\"err-message\":\"\",\"data\":[{\"success\":{} }]}}",
      "{\"response\":{\"err-message\":\"\",\"data\":[{\"success\":true},{\"success\":true}]}}",
      "{\"response\":{\"err-message\":\"unknown\",\"data\":[{\"success\":true}]}}"
  })
  void malformedAndUnknownEnvelopesFailClosed(String json) throws Exception {
    stub(json);
    StepVerifier.create(adapter.check(command)).expectNext(PasswordCheckResult.UNAVAILABLE).verifyComplete();
  }

  @Test
  void missingFailCountAndUnknownFieldsDoNotOverrideExplicitBoolean() throws Exception {
    stub("{\"extra\":1,\"response\":{\"extra\":1,\"err-message\":\"\",\"data\":[{\"success\":true,\"extra\":{}}]}}");
    StepVerifier.create(adapter.check(command)).expectNext(PasswordCheckResult.CORRECT).verifyComplete();
  }

  @Test
  void emptyAsyncAndSynchronousFailuresReturnTechnicalOutcomeWithoutRetryOrRawDetails() {
    when(executor.postEnvelope(any())).thenReturn(Mono.empty());
    StepVerifier.create(adapter.check(command)).expectNext(PasswordCheckResult.UNAVAILABLE).verifyComplete();
    when(executor.postEnvelope(any())).thenReturn(Mono.error(new IllegalStateException("test-only-detail")));
    StepVerifier.create(adapter.check(command)).expectNext(PasswordCheckResult.UNAVAILABLE).verifyComplete();
    when(executor.postEnvelope(any())).thenThrow(new IllegalStateException("test-only-detail"));
    StepVerifier.create(adapter.check(command)).expectNext(PasswordCheckResult.UNAVAILABLE).verifyComplete();
    verify(executor, times(3)).postEnvelope(any());
  }

  private void stub(String json) throws Exception {
    ApimResponseEnvelope<TrpPwdValidationDataItem> envelope =
        mapper.readValue(json, mapper.getTypeFactory()
            .constructParametricType(ApimResponseEnvelope.class, TrpPwdValidationDataItem.class));
    when(executor.<TrpPwdValidationRequest, TrpPwdValidationDataItem>postEnvelope(any()))
        .thenReturn(Mono.just(envelope));
  }
}
