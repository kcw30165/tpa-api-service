package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared;

import com.bct.ngtpa.apiservice.application.idempotency.ActionTaggedIdempotencyResponseEnvelope;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import java.util.Objects;

/** Login action-mapping compatibility API over the central envelope protocol. */
public final class LoginSharedResponseEnvelope {
  private final ActionTaggedIdempotencyResponseEnvelope delegate;

  public LoginSharedResponseEnvelope(TransientJsonPort json) {
    this.delegate = new ActionTaggedIdempotencyResponseEnvelope(Objects.requireNonNull(json));
  }

  public String encode(LoginSharedIdempotencyAction action, Object payload) {
    Objects.requireNonNull(action, "Login idempotency action must not be null");
    Objects.requireNonNull(payload, "Login response payload must not be null");
    try {
      return delegate.encode(action.action(), payload);
    } catch (RuntimeException exception) {
      throw invalid();
    }
  }

  public LoginSharedIdempotencyAction decodeAction(String serializedBody) {
    try {
      return action(delegate.decodeAction(serializedBody));
    } catch (UnsupportedActionException exception) {
      throw new IllegalArgumentException("Login replay action is unsupported");
    } catch (RuntimeException exception) {
      throw invalid();
    }
  }

  public <T> T decodePayload(
      LoginSharedIdempotencyAction expectedAction,
      String serializedBody,
      Class<T> payloadType) {
    Objects.requireNonNull(expectedAction, "Expected Login idempotency action must not be null");
    Objects.requireNonNull(payloadType, "Login response payload type must not be null");
    try {
      String actualAction = delegate.decodeAction(serializedBody);
      LoginSharedIdempotencyAction actual = action(actualAction);
      if (actual != expectedAction) {
        throw new ActionMismatchException();
      }
      return delegate.decodePayload(expectedAction.action(), serializedBody, payloadType);
    } catch (ActionMismatchException exception) {
      throw new IllegalArgumentException("Login replay action mismatch");
    } catch (UnsupportedActionException exception) {
      throw new IllegalArgumentException("Login replay action is unsupported");
    } catch (RuntimeException exception) {
      throw invalid();
    }
  }

  private LoginSharedIdempotencyAction action(String value) {
    for (LoginSharedIdempotencyAction action : LoginSharedIdempotencyAction.values()) {
      if (action.action().value().equals(value)) return action;
    }
    throw new UnsupportedActionException();
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("Login replay response decoding failed");
  }

  private static final class ActionMismatchException extends RuntimeException {}

  private static final class UnsupportedActionException extends RuntimeException {}
}
