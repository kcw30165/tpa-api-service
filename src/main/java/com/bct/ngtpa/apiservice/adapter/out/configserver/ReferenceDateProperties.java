package com.bct.ngtpa.apiservice.adapter.out.configserver;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reference-date")
@Getter
@Setter
public class ReferenceDateProperties {

    private String accountEnv = "";
    private String overrideDate = "";
    private String overrideZoneId = "";
    /** TTL in seconds for Redis populate after a Config Service hit. 0 = skip populate. */
    private long cacheTtlSeconds = 0;
}
