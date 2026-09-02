package com.bct.ngtpa.apiservice.adapter.out.redis.idempotency;

import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;

/** Derives a non-disclosing deterministic persistence locator for a logical operation. */
public interface IdempotencyPersistenceIdDeriver {
  String derive(IdempotencyOperationId operationId);
}
