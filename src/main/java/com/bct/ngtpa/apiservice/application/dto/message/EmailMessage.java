package com.bct.ngtpa.apiservice.application.dto.message;

public record EmailMessage(
    String recipient, String subject, String body, String from, String correlationId) {}
