package com.bct.ngtpa.apiservice.adapter.in.web.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdatePersonalInformationRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.usecase.personalinformation.PersonalInformationAccessConfigService;
import com.bct.ngtpa.apiservice.application.usecase.personalinformation.PersonalInformationAccessConfigService.Resolved;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Sub-task 3.4c: the PUT access check behind the controller (design A.3, D-13, D-14). */
class PersonalInformationFieldAccessCheckerTest {

  private static final AuthenticatedSessionId SID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
  private static final String ACCOUNT_REF = "acc_abcdefghijklmnopqrstuvwx";
  private static final String REQUEST_ID = "req-1";
  private static final Map<String, String> CACHED = Map.of("email", "READONLY");
  private static final Map<String, String> FRESH = Map.of("email", "EDITABLE_OPTION");
  private static final UpdatePersonalInformationRequest REQUEST =
      new UpdatePersonalInformationRequest("1.0", false, Map.of("emailAddress", "a@b.test"));
  private static final GetPersonalInformationCommand COMMAND =
      new GetPersonalInformationCommand(
          "en", "JP", "policy-1", "cert-1", "user-1", ActorUserRole.ROLE_USER_MBR);
  private static final ApiError NOT_EDITABLE =
      ApiError.field(
          "personalInformation.field.notEditable", null, List.of("emailAddress"), "SERVER");
  private static final ApiError REQUIRED =
      ApiError.field("personalInformation.field.required", null, List.of("emailAddress"), "SERVER");

  private final PersonalInformationAccessConfigService configService =
      mock(PersonalInformationAccessConfigService.class);
  private final PersonalInformationFieldAccessValidator validator =
      mock(PersonalInformationFieldAccessValidator.class);

  private PersonalInformationFieldAccessChecker checker() {
    return new PersonalInformationFieldAccessChecker(configService, validator);
  }

  private Mono<List<ApiError>> check(Optional<AuthenticatedSessionId> sessionId) {
    return checker().check(REQUEST, COMMAND, sessionId, ACCOUNT_REF, REQUEST_ID);
  }

  @Test
  void cachedConfigWithNoAccessErrorsAllowsTheUpdate() {
    when(configService.resolve(COMMAND, Optional.of(SID), ACCOUNT_REF, REQUEST_ID))
        .thenReturn(Mono.just(new Resolved(CACHED, true)));
    when(validator.validate(REQUEST, CACHED)).thenReturn(List.of());

    StepVerifier.create(check(Optional.of(SID)))
        .assertNext(errors -> assertThat(errors).isEmpty())
        .verifyComplete();

    verify(configService).resolve(COMMAND, Optional.of(SID), ACCOUNT_REF, REQUEST_ID);
    verify(configService, never()).refresh(any(), any(), any(), any());
  }

  @Test
  void aRejectionDecidedFromTheCacheIsRevalidatedAndAcceptedWhenTheFreshConfigAllowsIt() {
    when(configService.resolve(COMMAND, Optional.of(SID), ACCOUNT_REF, REQUEST_ID))
        .thenReturn(Mono.just(new Resolved(CACHED, true)));
    when(configService.refresh(COMMAND, Optional.of(SID), ACCOUNT_REF, REQUEST_ID))
        .thenReturn(Mono.just(FRESH));
    when(validator.validate(REQUEST, CACHED)).thenReturn(List.of(NOT_EDITABLE));
    when(validator.validate(REQUEST, FRESH)).thenReturn(List.of());

    StepVerifier.create(check(Optional.of(SID)))
        .assertNext(errors -> assertThat(errors).as("a stale cache must not block").isEmpty())
        .verifyComplete();

    verify(configService).refresh(COMMAND, Optional.of(SID), ACCOUNT_REF, REQUEST_ID);
  }

  @Test
  void aRejectionDecidedFromTheCacheIsConfirmedByTheFreshConfig() {
    when(configService.resolve(COMMAND, Optional.of(SID), ACCOUNT_REF, REQUEST_ID))
        .thenReturn(Mono.just(new Resolved(CACHED, true)));
    when(configService.refresh(COMMAND, Optional.of(SID), ACCOUNT_REF, REQUEST_ID))
        .thenReturn(Mono.just(FRESH));
    when(validator.validate(REQUEST, CACHED)).thenReturn(List.of(NOT_EDITABLE));
    when(validator.validate(REQUEST, FRESH)).thenReturn(List.of(REQUIRED));

    StepVerifier.create(check(Optional.of(SID)))
        .assertNext(errors -> assertThat(errors).containsExactly(REQUIRED))
        .verifyComplete();
  }

  @Test
  void aRejectionDecidedFromFreshlyFetchedConfigIsNotRefetched() {
    when(configService.resolve(COMMAND, Optional.of(SID), ACCOUNT_REF, REQUEST_ID))
        .thenReturn(Mono.just(new Resolved(FRESH, false)));
    when(validator.validate(REQUEST, FRESH)).thenReturn(List.of(NOT_EDITABLE));

    StepVerifier.create(check(Optional.of(SID)))
        .assertNext(errors -> assertThat(errors).containsExactly(NOT_EDITABLE))
        .verifyComplete();

    verify(configService, never()).refresh(any(), any(), any(), any());
  }

  @Test
  void withoutASessionTheFreshConfigIsUsed() {
    when(configService.resolve(COMMAND, Optional.empty(), ACCOUNT_REF, REQUEST_ID))
        .thenReturn(Mono.just(new Resolved(FRESH, false)));
    when(validator.validate(REQUEST, FRESH)).thenReturn(List.of());

    StepVerifier.create(check(Optional.empty()))
        .assertNext(errors -> assertThat(errors).isEmpty())
        .verifyComplete();

    verify(configService).resolve(COMMAND, Optional.empty(), ACCOUNT_REF, REQUEST_ID);
  }

  @Test
  void aLookupFailureIsPropagated() {
    var failure = new IllegalStateException("downstream unavailable");
    when(configService.resolve(COMMAND, Optional.of(SID), ACCOUNT_REF, REQUEST_ID))
        .thenReturn(Mono.error(failure));

    StepVerifier.create(check(Optional.of(SID)))
        .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
        .verify();

    verifyNoInteractions(validator);
  }

  @Test
  void aRefreshFailureIsPropagated() {
    var failure = new IllegalStateException("downstream unavailable");
    when(configService.resolve(COMMAND, Optional.of(SID), ACCOUNT_REF, REQUEST_ID))
        .thenReturn(Mono.just(new Resolved(CACHED, true)));
    when(configService.refresh(COMMAND, Optional.of(SID), ACCOUNT_REF, REQUEST_ID))
        .thenReturn(Mono.error(failure));
    when(validator.validate(REQUEST, CACHED)).thenReturn(List.of(NOT_EDITABLE));

    StepVerifier.create(check(Optional.of(SID)))
        .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
        .verify();
  }

  @Test
  void aRequestWithoutFieldsSkipsTheLookup() {
    for (UpdatePersonalInformationRequest request :
        List.of(
            new UpdatePersonalInformationRequest("1.0", false, Map.of()),
            new UpdatePersonalInformationRequest("1.0", false, null))) {
      StepVerifier.create(
              checker().check(request, COMMAND, Optional.of(SID), ACCOUNT_REF, REQUEST_ID))
          .assertNext(errors -> assertThat(errors).isEmpty())
          .verifyComplete();
    }

    verifyNoInteractions(configService, validator);
  }

  @Test
  void aNullRequestSkipsTheLookup() {
    StepVerifier.create(checker().check(null, COMMAND, Optional.of(SID), ACCOUNT_REF, REQUEST_ID))
        .assertNext(errors -> assertThat(errors).isEmpty())
        .verifyComplete();

    verifyNoInteractions(configService, validator);
  }

  @Test
  void theAccessCheckerRequiresItsArguments() {
    var checker = checker();

    assertThatThrownBy(() -> new PersonalInformationFieldAccessChecker(null, validator))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new PersonalInformationFieldAccessChecker(configService, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> checker.check(REQUEST, null, Optional.of(SID), ACCOUNT_REF, REQUEST_ID))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> checker.check(REQUEST, COMMAND, null, ACCOUNT_REF, REQUEST_ID))
        .isInstanceOf(NullPointerException.class);
  }
}
