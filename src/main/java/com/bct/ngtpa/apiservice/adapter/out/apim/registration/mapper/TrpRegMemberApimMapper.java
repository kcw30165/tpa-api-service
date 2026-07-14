package com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpRegMemberApimRequest;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.RegistrationCompletionCommand;
import java.util.Objects;

public class TrpRegMemberApimMapper {
    public TrpRegMemberApimRequest toRequest(RegistrationCompletionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return new TrpRegMemberApimRequest(command.accountEnv(), command.trustCode(),
                command.customerId(), command.userId(), command.password(), command.dateOfBirth(),
                command.mobileNumber(), command.email());
    }
}
