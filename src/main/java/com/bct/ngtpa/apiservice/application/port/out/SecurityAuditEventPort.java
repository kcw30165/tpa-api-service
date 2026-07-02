package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.security.audit.SecurityAuditEvent;
import reactor.core.publisher.Mono;

public interface SecurityAuditEventPort {

    Mono<Void> publish(SecurityAuditEvent event);
}
