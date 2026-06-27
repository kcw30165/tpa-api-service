package com.bct.ngtpa.apiservice.application.service.message;

public interface MessageTemplateRenderer {

    MessageTemplateRenderResult render(MessageTemplateRenderCommand command);
}