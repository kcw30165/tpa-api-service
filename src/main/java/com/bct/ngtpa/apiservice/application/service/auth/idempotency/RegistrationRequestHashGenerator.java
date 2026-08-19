package com.bct.ngtpa.apiservice.application.service.auth.idempotency;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyAction;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationRequestHash;
import java.util.List;

public interface RegistrationRequestHashGenerator {
  RegistrationRequestHash hash(RegistrationIdempotencyAction action, List<String> normalizedParts);
}
