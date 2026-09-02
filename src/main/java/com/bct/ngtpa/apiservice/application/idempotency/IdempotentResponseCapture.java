package com.bct.ngtpa.apiservice.application.idempotency;

/**
 * Action integration boundary that removes protected response material before persistence and
 * stores it separately when required. Implementations must return matching hydrated and safe forms.
 */
public interface IdempotentResponseCapture {
  IdempotentCapturedResponse capture(HydratedIdempotentResponse response);
}
