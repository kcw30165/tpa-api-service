package com.bct.ngtpa.apiservice.application.observability;

/** Per-invocation failure logging disposition carried by application commands. */
public interface LogExecutionFailurePolicy {
  boolean warningOnlyOnFailure();
}
