package com.bct.ngtpa.apiservice.adapter.out.redis.auth.compatibility;

import java.util.concurrent.atomic.AtomicBoolean;

public final class AuthFlowSessionPersistenceCompatibilityState {
  private final AtomicBoolean established = new AtomicBoolean(false);

  public void establish() {
    established.compareAndSet(false, true);
  }

  public boolean established() {
    return established.get();
  }

  public void requireEstablished() {
    if (!established())
      throw new IllegalStateException("auth-flow session persistence schema is incompatible");
  }
}
