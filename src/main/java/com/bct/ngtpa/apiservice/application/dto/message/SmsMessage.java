package com.bct.ngtpa.apiservice.application.dto.message;

public record SmsMessage(
                String recipient,
                String content,
                String country,
                String correlationId) {
}