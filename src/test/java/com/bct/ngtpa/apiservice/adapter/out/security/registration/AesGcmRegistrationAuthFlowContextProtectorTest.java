package com.bct.ngtpa.apiservice.adapter.out.security.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import org.junit.jupiter.api.Test;

class AesGcmRegistrationAuthFlowContextProtectorTest {

    private final AesGcmRegistrationAuthFlowContextProtectorAdapter protector =
            new AesGcmRegistrationAuthFlowContextProtectorAdapter("v1", "0123456789abcdef0123456789abcdef");

    @Test
    void contextProtectorRoundTripsContext() {
        var context = context();

        var encrypted = protector.protect(context);
        var decrypted = protector.unprotect(encrypted);

        assertThat(decrypted).isEqualTo(context);
        assertThat(encrypted.ciphertext()).isNotBlank();
        assertThat(encrypted.toString()).doesNotContain("3024102", "SHMW.ZIVIXV@HCGLT.NLA", encrypted.ciphertext());
    }

    @Test
    void contextProtectorUsesDifferentCiphertextForSamePlaintext() {
        var first = protector.protect(context());
        var second = protector.protect(context());

        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
    }

    @Test
    void contextProtectorRejectsTamperedCiphertextSafely() {
        var encrypted = protector.protect(context());
        var tampered = encrypted.ciphertext().substring(0, encrypted.ciphertext().length() - 2) + "xx";

        assertThatThrownBy(() -> protector.unprotect(encrypted.withCiphertext(tampered)))
                .isInstanceOf(RegistrationAuthFlowContextProtectionException.class)
                .hasMessageContaining("Unable to unprotect registration context")
                .hasMessageNotContaining(tampered)
                .hasMessageNotContaining("3024102")
                .hasMessageNotContaining("SHMW.ZIVIXV@HCGLT.NLA");
    }

    @Test
    void contextProtectorFailsFastWhenSecretMissingOrInvalid() {
        assertThatThrownBy(() -> new AesGcmRegistrationAuthFlowContextProtectorAdapter("v1", "short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("registration context protection secret");
    }

    private static RegistrationAuthFlowContext context() {
        return new RegistrationAuthFlowContext(
                RegistrationIdentityType.ID,
                "3024102",
                "04/04/1982",
                RegistrationContactType.EMAIL,
                "SHMW.ZIVIXV@HCGLT.NLA");
    }
}
