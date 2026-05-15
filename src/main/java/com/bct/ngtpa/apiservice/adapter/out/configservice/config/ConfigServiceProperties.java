package com.bct.ngtpa.apiservice.adapter.out.configservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "config-service")
@Getter
@Setter
public class ConfigServiceProperties {

    private String baseUrl = "";
    private int timeoutMilliseconds = 10000;
    private String username = "";
    private String password = "";
}
