package com.bct.ngtpa.apiservice.adapter.out.email;

import reactor.core.publisher.Mono;

public interface SmtpClient {

    Mono<Void> send(SmtpSendCommand command);
}
