package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.message.SenderResult;
import com.bct.ngtpa.apiservice.application.dto.message.SmsMessage;
import reactor.core.publisher.Mono;

public interface SmsSenderPort {

  Mono<SenderResult> sendSms(SmsMessage message);
}
