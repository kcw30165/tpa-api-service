package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.context.UsernameRecoveryAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.context.UsernameRecoveryAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;

public interface UsernameRecoveryAuthFlowContextProtectorPort {
  UsernameRecoveryAuthFlowEncryptedContext protect(
      AuthFlowSessionId authFlowSessionId, UsernameRecoveryAuthFlowContext context);

  UsernameRecoveryAuthFlowContext unprotect(
      AuthFlowSessionId authFlowSessionId,
      UsernameRecoveryAuthFlowEncryptedContext encryptedContext);
}
