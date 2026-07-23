package com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency;
import java.time.*;
public record LoginIdempotencyFailCommand(LoginIdempotencyAction action,LoginIdempotencyKey idempotencyKey,LoginCredentialRequestHash requestHash,String reasonCode,Instant failedAt,Duration ttl){}
