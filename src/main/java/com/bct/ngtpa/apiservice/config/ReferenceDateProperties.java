package com.bct.ngtpa.apiservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reference-date")
@Getter
@Setter
public class ReferenceDateProperties {

    private String zoneId = "Asia/Hong_Kong";
    private String nonProdOverride = "";
}