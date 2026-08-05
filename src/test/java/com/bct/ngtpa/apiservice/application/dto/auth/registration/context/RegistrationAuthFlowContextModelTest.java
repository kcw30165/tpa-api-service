package com.bct.ngtpa.apiservice.application.dto.auth.registration.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;

import org.junit.jupiter.api.Test;

class RegistrationAuthFlowContextModelTest {

    @Test
    void registrationAuthFlowContextRequiresRequiredFields() {
        var context = new RegistrationAuthFlowContext(
                MemberIdentityType.ID,
                "3024102",
                "04/04/1982",
                RegistrationContactType.EMAIL,
                "SHMW.ZIVIXV@HCGLT.NLA");

        assertThat(context.identityType()).isEqualTo(MemberIdentityType.ID);
        assertThat(context.contactType()).isEqualTo(RegistrationContactType.EMAIL);
        assertThatThrownBy(() -> new RegistrationAuthFlowContext(null, "3024102", "04/04/1982", RegistrationContactType.EMAIL, "x@y.test"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RegistrationAuthFlowContext(MemberIdentityType.ID, " ", "04/04/1982", RegistrationContactType.EMAIL, "x@y.test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identityValue");
    }

    @Test
    void registrationAuthFlowContextToStringRedactsPii() {
        var context = new RegistrationAuthFlowContext(
                MemberIdentityType.ID,
                "3024102",
                "04/04/1982",
                RegistrationContactType.EMAIL,
                "SHMW.ZIVIXV@HCGLT.NLA");

        assertThat(context.toString())
                .contains("ID", "EMAIL")
                .doesNotContain("3024102", "04/04/1982", "SHMW.ZIVIXV@HCGLT.NLA", "91234567");
    }

    @Test
    void encryptedContextContainsCiphertextOnly() {
        var encrypted = new RegistrationAuthFlowEncryptedContext("ciphertext-secret", "v1", "AES_GCM_V1");

        assertThat(encrypted.ciphertext()).isEqualTo("ciphertext-secret");
        assertThat(encrypted.keyVersion()).isEqualTo("v1");
        assertThat(encrypted.algorithm()).isEqualTo("AES_GCM_V1");
        assertThat(encrypted.toString()).doesNotContain("ciphertext-secret");
    }
}
