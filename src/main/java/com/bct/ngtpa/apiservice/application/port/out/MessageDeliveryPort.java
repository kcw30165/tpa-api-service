package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.port.out.authflow.AuthFlowMessagePort;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryCommand;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryResult;
import reactor.core.publisher.Mono;

public interface MessageDeliveryPort extends AuthFlowMessagePort {

    Mono<MessageDeliveryResult> deliver(MessageDeliveryCommand command);
}