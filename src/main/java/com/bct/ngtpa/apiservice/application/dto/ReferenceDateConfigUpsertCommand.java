package com.bct.ngtpa.apiservice.application.dto;

public record ReferenceDateConfigUpsertCommand(
        String configKey,
        String configValue) {
}