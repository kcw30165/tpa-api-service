package com.bct.ngtpa.apiservice.adapter.out.redis.auth.compatibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPersistenceCompatibilityPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPersistenceCompatibilityResult;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPersistenceCompatibilityStatus;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import reactor.core.publisher.Mono;

/**
 * Branch-oriented tests for {@link AuthFlowSessionPersistenceCompatibilityStartupGate}. All types
 * verified verbatim (rounds 171-172).
 */
class AuthFlowSessionPersistenceCompatibilityStartupGateTest {

  private final AuthFlowSessionPersistenceCompatibilityPort port =
      mock(AuthFlowSessionPersistenceCompatibilityPort.class);
  private final AuthFlowSessionPersistenceCompatibilityState state =
      new AuthFlowSessionPersistenceCompatibilityState();

  private AuthFlowSessionPersistenceSchemaProperties propertiesWithVersion(Integer version) {
    var properties = new AuthFlowSessionPersistenceSchemaProperties();
    properties.setPersistenceSchemaVersion(version);
    return properties;
  }

  private AuthFlowSessionPersistenceCompatibilityStartupGate newGate(Integer configuredVersion) {
    return new AuthFlowSessionPersistenceCompatibilityStartupGate(
        port, propertiesWithVersion(configuredVersion), state, Duration.ofSeconds(5));
  }

  // ----- properties.requiredVersion()'s own guards propagate -------------------------

  @Test
  void nullConfiguredVersionThrowsFromProperties() {
    assertThatThrownBy(() -> newGate(null).run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("required");
  }

  @Test
  void wrongConfiguredVersionThrowsFromProperties() {
    assertThatThrownBy(() -> newGate(99).run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("expectedVersion=2");
  }

  // ----- compatible result: establishes state, does not throw -------------------------

  @Test
  void compatibleResultEstablishesStateWithoutThrowing() {
    when(port.establish(2))
        .thenReturn(
            Mono.just(
                new AuthFlowSessionPersistenceCompatibilityResult(
                    AuthFlowSessionPersistenceCompatibilityStatus.COMPATIBLE, 2, 2)));
    newGate(2).run(mock(ApplicationArguments.class));
    assertThat(state.established()).isTrue();
  }

  @Test
  void initializedResultEstablishesStateWithoutThrowing() {
    when(port.establish(2))
        .thenReturn(
            Mono.just(
                new AuthFlowSessionPersistenceCompatibilityResult(
                    AuthFlowSessionPersistenceCompatibilityStatus.INITIALIZED, 2, 2)));
    newGate(2).run(mock(ApplicationArguments.class));
    assertThat(state.established()).isTrue();
  }

  // ----- incompatible result: throws, with actualVersion in the message -----------------

  @Test
  void incompatibleResultWithActualVersionThrows() {
    when(port.establish(2))
        .thenReturn(
            Mono.just(
                new AuthFlowSessionPersistenceCompatibilityResult(
                    AuthFlowSessionPersistenceCompatibilityStatus.EXISTING_VERSION_MISMATCH,
                    2,
                    5)));
    assertThatThrownBy(() -> newGate(2).run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("actualVersion=5");
    assertThat(state.established()).isFalse();
  }

  @Test
  void incompatibleResultWithNullActualVersionThrowsWithUnavailable() {
    when(port.establish(2))
        .thenReturn(
            Mono.just(
                new AuthFlowSessionPersistenceCompatibilityResult(
                    AuthFlowSessionPersistenceCompatibilityStatus.MARKER_CORRUPT, 2, null)));
    assertThatThrownBy(() -> newGate(2).run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("actualVersion=unavailable");
  }

  // ----- null result (e.g. block() timeout) throws with "unavailable" ------------------

  @Test
  void nullResultThrowsWithUnavailable() {
    when(port.establish(2)).thenReturn(Mono.empty());
    assertThatThrownBy(() -> newGate(2).run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("actualVersion=unavailable");
    assertThat(state.established()).isFalse();
  }

  // ----- underlying port error propagates through block() ------------------------------

  @Test
  void portErrorPropagates() {
    when(port.establish(2)).thenReturn(Mono.error(new IllegalStateException("boom")));
    assertThatThrownBy(() -> newGate(2).run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("boom");
  }
}
