package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenHasher;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043CentralCsrfValidationOutcomeTest {

    private final CsrfTokenHasher hasher = new CsrfTokenHasher() {
        @Override
        public String hash(String rawToken) {
            return "hash:" + rawToken;
        }

        @Override
        public boolean matches(String rawToken, String tokenHash) {
            return ("hash:" + rawToken).equals(tokenHash);
        }
    };

    @Test
    void centralServiceClassifiesMissingHashMismatchAndValidTokens() {
        var service = new CsrfTokenValidationService(hasher);

        assertThat(service.validate(null, "hash:csrf").status())
                .isEqualTo(CsrfTokenValidationStatus.TOKEN_MISSING);
        assertThat(service.validate("csrf", " ").status())
                .isEqualTo(CsrfTokenValidationStatus.TOKEN_HASH_MISSING);
        assertThat(service.validate("wrong", "hash:csrf").status())
                .isEqualTo(CsrfTokenValidationStatus.TOKEN_MISMATCH);
        assertThat(service.validate("csrf", "hash:csrf").status())
                .isEqualTo(CsrfTokenValidationStatus.VALID);
        assertThat(service.validate("csrf", "hash:csrf").valid()).isTrue();
    }

    @Test
    void centralServiceDoesNotAcceptNullHasher() {
        assertThatThrownBy(() -> new CsrfTokenValidationService(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("csrfTokenHasher");
    }

    @Test
    void bothPreAuthAndAuthenticatedValidationUseTheCentralTokenVerifier() throws Exception {
        String preAuth = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/session/authflow/AuthFlowSessionCsrfService.java"));
        String authenticated = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/session/authenticated/AuthenticatedSessionValidationService.java"));

        assertThat(preAuth).contains("CsrfTokenValidationService");
        assertThat(authenticated).contains("CsrfTokenValidationService");
        assertThat(authenticated).contains("AuthenticatedCsrfValidationException");
        assertThat(preAuth).doesNotContain("csrfTokenHasher.matches(command.submittedToken()") ;
        assertThat(authenticated).doesNotContain("csrfTokenHasher.matches(");
    }
}
