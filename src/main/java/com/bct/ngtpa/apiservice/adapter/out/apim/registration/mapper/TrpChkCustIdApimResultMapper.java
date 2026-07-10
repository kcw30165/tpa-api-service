package com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.TrpChkCustIdApimEnvelope;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.RegistrationUsernameAvailabilityResult;

public class TrpChkCustIdApimResultMapper {
    public RegistrationUsernameAvailabilityResult toResult(TrpChkCustIdApimEnvelope envelope) {
        String errMessage = envelope == null ? null : envelope.errMessage();
        if (errMessage == null || errMessage.isBlank()) {
            return RegistrationUsernameAvailabilityResult.available();
        }
        return RegistrationUsernameAvailabilityResult.rejected(errMessage.trim());
    }
}
