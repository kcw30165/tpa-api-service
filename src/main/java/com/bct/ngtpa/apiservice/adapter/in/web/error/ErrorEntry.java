package com.bct.ngtpa.apiservice.adapter.in.web.error;

import java.util.List;
import java.util.Objects;

/**
 * One public error entry a mapping produces: the error type/code/targets, plus a {@link
 * MessagePolicy} stating whether the renderer should resolve a public message for this entry.
 *
 * <p>{@link ErrorDescriptor} carries a {@code List<ErrorEntry>} so {@code WebExchangeBindException}
 * (one entry per field error) and the three null-message exceptions (one entry each, {@link
 * MessagePolicy#NONE}) can both be expressed without changing the shape of every other mapping,
 * which produces exactly one {@link MessagePolicy#RESOLVE} entry via {@link
 * ErrorDescriptor#single}.
 */
public record ErrorEntry(String type, String code, List<String> targets, MessagePolicy policy) {

  public ErrorEntry {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(policy, "policy");
    targets = targets == null ? List.of() : List.copyOf(targets);
  }
}
