package com.bct.ngtpa.apiservice.application.idempotency;

/** Canonical serialization contract for the shared terminal response snapshot. */
public interface IdempotentResponseSnapshotCodec {
  String encode(IdempotentTerminalResponseSnapshot snapshot);

  IdempotentTerminalResponseSnapshot decode(String value);
}
