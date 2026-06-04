package com.bct.ngtpa.apiservice.application.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record MemberInfoConfigItem(
        @JsonProperty("item-id")
        @JsonAlias("itemId")
        String itemId,

        @JsonProperty("item-name")
        @JsonAlias("itemName")
        String itemName,

        @JsonProperty("item-type")
        @JsonAlias("itemType")
        MemberInfoConfigItemType itemType,

        String function,

        @JsonProperty("sch-type")
        @JsonAlias("schType")
        String schType,

        @JsonProperty("config-value")
        @JsonAlias("configValue")
        String configValue
) {
    public MemberInfoConfigItem {
        itemId = trimToNull(itemId);
        itemName = trimToNull(itemName);
        itemType = itemType == null ? MemberInfoConfigItemType.UNKNOWN : itemType;
        function = trimToNull(function);
        schType = trimToNull(schType);
        configValue = trimToNull(configValue);
    }

    public static MemberInfoConfigItem of(String itemId, MemberInfoConfigItemType itemType, String configValue) {
        return new MemberInfoConfigItem(itemId, null, itemType, null, null, configValue);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
