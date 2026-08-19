package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

public final class ForgotPasswordMutationLeaseBudget {
  private final long deadline;
  private final LongSupplier nanos;

  public ForgotPasswordMutationLeaseBudget(Duration lease, LongSupplier n) {
    if (lease == null || lease.isZero() || lease.isNegative())
      throw new IllegalArgumentException("lease must be positive");
    nanos = Objects.requireNonNull(n);
    deadline = Math.addExact(n.getAsLong(), lease.toNanos());
  }

  public boolean hasPersistenceBudget(Duration required) {
    return required != null
        && !required.isNegative()
        && deadline - nanos.getAsLong() >= required.toNanos();
  }

  public boolean active() {
    return deadline - nanos.getAsLong() > 0;
  }
}
