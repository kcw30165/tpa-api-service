package com.bct.ngtpa.apiservice.application.port.out;

/** Derives an opaque keyed identity from the server-authenticated username. */
public interface PasswordValidationBucketIdPort {
  String derive(String normalizedUsername);
}
