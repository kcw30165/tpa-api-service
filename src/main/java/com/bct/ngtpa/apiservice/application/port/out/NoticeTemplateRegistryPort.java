package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryChannel;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryType;
import com.bct.ngtpa.apiservice.application.dto.message.NoticeTemplateDefinition;

public interface NoticeTemplateRegistryPort {
  NoticeTemplateDefinition resolve(
      MessageDeliveryType messageType, MessageDeliveryChannel channel, String locale);
}
