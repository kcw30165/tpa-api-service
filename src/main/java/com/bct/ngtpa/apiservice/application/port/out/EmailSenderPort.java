package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.message.EmailMessage;
import com.bct.ngtpa.apiservice.application.dto.message.SenderResult;
import reactor.core.publisher.Mono;

public interface EmailSenderPort {

  Mono<SenderResult> sendEmail(EmailMessage message);
}
