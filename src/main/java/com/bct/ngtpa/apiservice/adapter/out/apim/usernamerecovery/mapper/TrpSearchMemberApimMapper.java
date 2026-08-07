package com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.mapper;

import com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.dto.TrpSearchMemberApimRequest;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupCommand;
import java.util.Objects;

/**
 * Pure mapper from the application lookup command to the TRPSearchMember APIM
 * request. Identity mapping is direct ({@code GPN}, {@code GPFS},
 * {@code STAFFNO}, {@code ID}) and no raw APIM names leave this package. A null
 * contact value is sent as an empty string by the request DTO because APIM
 * requires the field.
 */
public class TrpSearchMemberApimMapper {

    public TrpSearchMemberApimRequest toApimRequest(UsernameRecoveryLookupCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return new TrpSearchMemberApimRequest(
                command.identityType().name(),
                command.identityValue(),
                command.dateOfBirth(),
                command.email(),
                command.mobile());
    }
}
