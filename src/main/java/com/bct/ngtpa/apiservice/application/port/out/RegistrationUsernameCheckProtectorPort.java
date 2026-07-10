package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.RegistrationUsernameCheckEncryptedState;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.UsernameCheckState;

public interface RegistrationUsernameCheckProtectorPort {
    RegistrationUsernameCheckEncryptedState protect(UsernameCheckState usernameCheckState);

    UsernameCheckState unprotect(RegistrationUsernameCheckEncryptedState encryptedState);
}
