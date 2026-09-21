package com.bct.ngtpa.apiservice.adapter.out.apim.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.config.TrpMemberLoginProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.dto.TrpMemberLoginApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.dto.TrpMemberLoginApimRequest;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationCommand;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Branch-oriented reactive tests for {@link SharedApimTrpMemberLoginTransport}. All types verified
 * verbatim (rounds 175-176). The existing
 * TrpMemberLoginAuthenticatedExecutorBoundaryTest (confirmed round 176) is a PURE
 * textual/architectural check (reads source as strings, asserts on substrings) - it never invokes
 * {@code .post()} or exercises real behavior, so this test is genuinely new, not a duplicate.
 */
class SharedApimTrpMemberLoginTransportTest {

  private final ApimExchangeExecutor executor = mock(ApimExchangeExecutor.class);

  private TrpMemberLoginProperties propertiesWithPath(String path) {
    var properties = new TrpMemberLoginProperties();
    properties.setPath(path);
    return properties;
  }

  private SharedApimTrpMemberLoginTransport newTransport(String path) {
    return new SharedApimTrpMemberLoginTransport(executor, propertiesWithPath(path));
  }

  private CredentialAuthenticationCommand command() {
    return new CredentialAuthenticationCommand(
        "user1", "pass1", "en", "req-1", ActorUserRole.ROLE_USER_MBR);
  }

  // ----- constructor guards ----------------------------------------------------

  @Test
  void constructorRejectsNullExecutor() {
    assertThatThrownBy(
            () -> new SharedApimTrpMemberLoginTransport(null, propertiesWithPath("/path")))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void constructorRejectsNullLoginProperties() {
    assertThatThrownBy(() -> new SharedApimTrpMemberLoginTransport(executor, null))
        .isInstanceOf(NullPointerException.class);
  }

  // ----- post(): guards --------------------------------------------------------

  @Test
  void postRejectsNullCommand() {
    assertThatThrownBy(() -> newTransport("/path").post(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void postWithNullConfiguredPathThrows() {
    assertThatThrownBy(() -> newTransport(null).post(command()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("apim.login.member-login.path");
  }

  @Test
  void postWithBlankConfiguredPathThrows() {
    assertThatThrownBy(() -> newTransport("   ").post(command()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("apim.login.member-login.path");
  }

  // ----- post(): success path delegates to the executor with a correct exchange -----------

  @SuppressWarnings("unchecked")
  @Test
  void postDelegatesToExecutorAndReturnsEnvelope() {
    ApimResponseEnvelope<TrpMemberLoginApimDataItem> envelope = mock(ApimResponseEnvelope.class);
    when(executor.postEnvelope(any())).thenReturn((Mono) Mono.just(envelope));
    StepVerifier.create(newTransport("/ws/login").post(command()))
        .expectNext(envelope)
        .verifyComplete();
  }

  @SuppressWarnings("unchecked")
  @Test
  void postTrimsConfiguredPathBeforeUse() {
    ApimResponseEnvelope<TrpMemberLoginApimDataItem> envelope = mock(ApimResponseEnvelope.class);
    when(executor.postEnvelope(any())).thenReturn((Mono) Mono.just(envelope));
    newTransport("  /ws/login  ").post(command()).block();
    verify(executor)
        .postEnvelope(
            org.mockito.ArgumentMatchers.argThat(
                (ApimExchangeRequest<TrpMemberLoginApimRequest, TrpMemberLoginApimDataItem> req) -> "/ws/login".equals(req.path())));
  }

  // ----- post(): executor error propagates ---------------------------------------

  @Test
  void postExecutorErrorPropagates() {
    when(executor.postEnvelope(any())).thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newTransport("/ws/login").post(command()))
        .expectError(IllegalStateException.class)
        .verify();
  }
}
