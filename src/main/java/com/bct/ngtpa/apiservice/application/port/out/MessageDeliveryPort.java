package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryCommand;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryResult;
import reactor.core.publisher.Mono;

public interface MessageDeliveryPort {

    Mono<MessageDeliveryResult> deliver(MessageDeliveryCommand command);
}