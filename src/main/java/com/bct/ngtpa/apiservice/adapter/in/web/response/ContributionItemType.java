package com.bct.ngtpa.apiservice.adapter.in.web.response;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Type discriminator for contribution list items.
 * Serialized as lowercase string value in JSON.
 */
public enum ContributionItemType {

    CONTRIBUTION("contribution");

    private final String value;

    ContributionItemType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
