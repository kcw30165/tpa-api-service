package com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimRequest;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.RegistrationUsernameAvailabilityCommand;
import java.util.Objects;

public class TrpChkCustIdApimMapper {
    public TrpChkCustIdApimRequest toApimRequest(RegistrationUsernameAvailabilityCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(command.selectedTrust(), "selectedTrust must not be null");
        return new TrpChkCustIdApimRequest(
                command.username());
    }
}
