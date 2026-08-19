package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.RegistrationAuthWorkflowPolicy;
import java.util.Optional;

public interface AuthWorkflowPolicyPort {

  RegistrationAuthWorkflowPolicy registrationPolicy();

  Optional<String> resolveIdentityTypeSearchScope(String identityType);
}
