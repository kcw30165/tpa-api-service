package com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.TrpChkCustIdApimEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimDataItem;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.RegistrationUsernameAvailabilityResult;
import java.util.List;

public class TrpChkCustIdApimResultMapper {
    private static final String USERNAME_TAKEN_DOWNSTREAM_CODE = "err.tpa.username.taken";

    public RegistrationUsernameAvailabilityResult toResult(TrpChkCustIdApimEnvelope envelope) {
        String errMessage = envelope == null ? null : envelope.errMessage();
        if (errMessage != null && !errMessage.isBlank()) {
            return RegistrationUsernameAvailabilityResult.rejected(errMessage.trim());
        }

        List<TrpChkCustIdApimDataItem> data = envelope == null || envelope.data() == null
                ? List.of()
                : envelope.data();
        boolean existingCustomerId = data.stream().anyMatch(TrpChkCustIdApimDataItem::exists);
        if (existingCustomerId) {
            return RegistrationUsernameAvailabilityResult.rejected(USERNAME_TAKEN_DOWNSTREAM_CODE);
        }
        return RegistrationUsernameAvailabilityResult.available();
    }
}
