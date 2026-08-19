package com.bct.ngtpa.apiservice.application.service.auth.login.idempotency;

import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.*;

public interface LoginIdempotencyPersistenceIdDeriver {
  LoginIdempotencyPersistenceId derive(
      LoginIdempotencyAction action, LoginIdempotencyKey publicKey);
}
