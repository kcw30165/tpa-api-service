package com.bct.ngtpa.apiservice.adapter.out.security.forgotpassword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.ForgotPasswordResetAuthorization;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AesGcmForgotPasswordAuthFlowContextProtectorTest {

    private final AesGcmForgotPasswordAuthFlowContextProtectorAdapter protector = new AesGcmForgotPasswordAuthFlowContextProtectorAdapter(
            "v1", "0123456789abcdef0123456789abcdef");

    @Test
    void protectsAndUnprotectsForgotPasswordContextWithoutLeakingPlaintext() {
        var context = context();

        var encrypted = protector.protect(context);
        var decrypted = protector.unprotect(encrypted);

        assertThat(decrypted).isEqualTo(context);
        assertThat(encrypted.ciphertext()).isNotBlank();
        assertThat(encrypted.ciphertext()).doesNotContain("member01", "tas-user-01", "member@example.com");
        assertThat(context.toString()).doesNotContain("member01", "tas-user-01", "member@example.com", "authz-123");
        assertThat(encrypted.toString()).doesNotContain(encrypted.ciphertext());
    }

    @Test
    void rejectsTamperedCiphertextWithoutEchoingProtectedValues() {
        var encrypted = protector.protect(context());
        var tampered = encrypted.ciphertext().substring(0, encrypted.ciphertext().length() - 2) + "xx";

        assertThatThrownBy(() -> protector.unprotect(encrypted.withCiphertext(tampered)))
                .isInstanceOf(ForgotPasswordAuthFlowContextProtectionException.class)
                .hasMessageContaining("Unable to unprotect forgot password context")
                .hasMessageNotContaining(tampered)
                .hasMessageNotContaining("member01")
                .hasMessageNotContaining("member@example.com");
    }

    private static ForgotPasswordAuthFlowContext context() {
        return new ForgotPasswordAuthFlowContext(
                "member01",
                "tas-user-01",
                "ID",
                true,
                "reset-subject-01",
                "EMAIL",
                "member@example.com",
                "m****r@example.com",
                new ForgotPasswordResetAuthorization(
                        "authz-123",
                        "afs_ctx",
                        "reset-subject-01",
                        Instant.parse("2026-07-30T00:00:00Z"),
                        Instant.parse("2026-07-30T00:05:00Z"),
                        null),
                "v1");
    }
}