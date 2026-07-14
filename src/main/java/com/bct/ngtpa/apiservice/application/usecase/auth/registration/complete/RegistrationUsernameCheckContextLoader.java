package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationUsernameCheckProtectorPort;
import java.util.Map;
import java.util.Objects;

public class RegistrationUsernameCheckContextLoader {
    private final RegistrationUsernameCheckProtectorPort protector;
    public RegistrationUsernameCheckContextLoader(RegistrationUsernameCheckProtectorPort protector) {
        this.protector = Objects.requireNonNull(protector, "protector must not be null");
    }
    public UsernameCheckState load(AuthFlowSession session) {
        if (session == null || session.data() == null) throw new IllegalStateException("Username check context is missing");
        Map<String,String> a=session.data().attributes();
        String ciphertext=a.get(RegistrationUsernameCheckAttributeNames.USERNAME_CHECK_CIPHERTEXT);
        String keyVersion=a.get(RegistrationUsernameCheckAttributeNames.USERNAME_CHECK_KEY_VERSION);
        String algorithm=a.get(RegistrationUsernameCheckAttributeNames.USERNAME_CHECK_ALGORITHM);
        if (ciphertext==null||ciphertext.isBlank()||keyVersion==null||keyVersion.isBlank()) throw new IllegalStateException("Username check context is missing");
        return protector.unprotect(new RegistrationUsernameCheckEncryptedState(ciphertext,keyVersion,algorithm));
    }
}
