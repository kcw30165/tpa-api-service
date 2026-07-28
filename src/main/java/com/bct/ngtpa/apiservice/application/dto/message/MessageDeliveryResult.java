package com.bct.ngtpa.apiservice.application.dto.message;

public record MessageDeliveryResult(
                MessageDeliveryStatus status,
                MessageDeliveryChannel channel,
                String providerMessageId,
                String failureCode,
                String failureMessage) {
}