package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete;

/** Indicates registration completion failed before the downstream request could be dispatched. */
public final class RegistrationCompletionPreDispatchException extends RuntimeException {
  public RegistrationCompletionPreDispatchException(Throwable cause) {
    super("Registration completion failed before downstream dispatch", cause);
  }
}
