package com.bct.ngtpa.apiservice.shared.logging;

/** Per-invocation logging disposition for failures observed by {@link LogExecution}. */
public interface LogExecutionFailurePolicy {
  boolean warningOnlyOnFailure();
}
