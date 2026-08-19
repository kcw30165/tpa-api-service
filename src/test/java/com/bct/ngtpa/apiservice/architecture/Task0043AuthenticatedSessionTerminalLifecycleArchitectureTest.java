package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionLifecycleStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043AuthenticatedSessionTerminalLifecycleArchitectureTest {
  @Test
  void lifecycleVocabularyIncludesExistingDomainTerminalStates() {
    assertThat(AuthenticatedSessionLifecycleStatus.values())
        .contains(
            AuthenticatedSessionLifecycleStatus.LOGGED_OUT,
            AuthenticatedSessionLifecycleStatus.REVOKED,
            AuthenticatedSessionLifecycleStatus.EXPIRED);
  }

  @Test
  void terminalTransitionsPersistProtectedStateInsteadOfDeleting() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/session/authenticated/AuthenticatedSessionLifecycleService.java"));
    assertThat(source)
        .containsIgnoringWhitespaces("AuthenticatedSessionStatus.LOGGED_OUT")
        .containsIgnoringWhitespaces("AuthenticatedSessionStatus.REVOKED")
        .containsIgnoringWhitespaces("AuthenticatedSessionStatus.EXPIRED")
        .containsIgnoringWhitespaces("terminalRetention")
        .containsIgnoringWhitespaces("protector.protect(terminal)")
        .containsIgnoringWhitespaces("port.touch(new TouchAuthenticatedSessionCommand")
        .doesNotContain("port.delete(")
        .doesNotContain("port.revoke(");
  }

  @Test
  void logoutAndRevocationRemainSeparateCapabilities() throws Exception {
    String lifecycle =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/session/authenticated/AuthenticatedSessionLifecycleService.java"));
    assertThat(lifecycle)
        .containsIgnoringWhitespaces("implements LoadAndTouchAuthenticatedSessionUseCase")
        .containsIgnoringWhitespaces("LogoutAuthenticatedSessionUseCase")
        .containsIgnoringWhitespaces("RevokeAuthenticatedSessionUseCase");
  }
}
