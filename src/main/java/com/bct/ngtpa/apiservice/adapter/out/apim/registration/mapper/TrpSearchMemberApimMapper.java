package com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpSearchMemberApimRequest;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupCommand;
import java.util.Objects;

public class TrpSearchMemberApimMapper {
  public TrpSearchMemberApimRequest toApimRequest(RegistrationLookupCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    String email =
        command.contactType() == RegistrationContactType.EMAIL ? command.contactValue() : "";
    String mobileNumber =
        command.contactType() == RegistrationContactType.MOBILE ? command.contactValue() : "";
    return new TrpSearchMemberApimRequest(
        command.identityType().name(),
        command.identityValue(),
        command.dateOfBirth(),
        email,
        mobileNumber);
  }
}
