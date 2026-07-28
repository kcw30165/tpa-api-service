package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete;

import static org.assertj.core.api.Assertions.assertThat;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationUsernameCheckProtectorPort;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RegistrationUsernameCheckContextLoaderTest {
    @Test
    void loadsOnlyEncryptedAttributes() {
        var expected = new UsernameCheckState(new SelectedTrust("TL", "ENV1"), "user1", Instant.now(), null);
        RegistrationUsernameCheckProtectorPort p = new RegistrationUsernameCheckProtectorPort() {
            public RegistrationUsernameCheckEncryptedState protect(UsernameCheckState s) {
                throw new UnsupportedOperationException();
            }

            public UsernameCheckState unprotect(RegistrationUsernameCheckEncryptedState e) {
                return expected;
            }
        };
        var data = new AuthFlowSessionData(null, null,
                Map.of(RegistrationUsernameCheckAttributeNames.USERNAME_CHECK_CIPHERTEXT, "enc",
                        RegistrationUsernameCheckAttributeNames.USERNAME_CHECK_KEY_VERSION, "v1",
                        RegistrationUsernameCheckAttributeNames.USERNAME_CHECK_ALGORITHM, "AES_GCM_V1"));
        var session = new AuthFlowSession(new AuthFlowSessionId("afs_safe"), AuthFlowScenario.REGISTRATION,
                AuthFlowStatus.USERNAME_VERIFIED, "h", null, data, Instant.now(), Instant.now(),
                Instant.now().plusSeconds(60));
        assertThat(new RegistrationUsernameCheckContextLoader(p).load(session)).isSameAs(expected);
    }
}
