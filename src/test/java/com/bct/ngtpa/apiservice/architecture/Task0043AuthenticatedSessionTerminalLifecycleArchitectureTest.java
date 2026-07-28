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
                String source = Files.readString(Path.of(
                                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/session/authenticated/AuthenticatedSessionLifecycleService.java"));
                assertThat(source)
                                .contains("AuthenticatedSessionStatus.LOGGED_OUT")
                                .contains("AuthenticatedSessionStatus.REVOKED")
                                .contains("AuthenticatedSessionStatus.EXPIRED")
                                .contains("terminalRetention")
                                .contains("protector.protect(terminal)")
                                .contains("port.touch(new TouchAuthenticatedSessionCommand")
                                .doesNotContain("port.delete(")
                                .doesNotContain("port.revoke(");
        }

        @Test
        void logoutAndRevocationRemainSeparateCapabilities() throws Exception {
                String lifecycle = Files.readString(Path.of(
                                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/session/authenticated/AuthenticatedSessionLifecycleService.java"));
                assertThat(lifecycle)
                                .contains("implements LoadAndTouchAuthenticatedSessionUseCase")
                                .contains("LogoutAuthenticatedSessionUseCase")
                                .contains("RevokeAuthenticatedSessionUseCase");
        }
}
