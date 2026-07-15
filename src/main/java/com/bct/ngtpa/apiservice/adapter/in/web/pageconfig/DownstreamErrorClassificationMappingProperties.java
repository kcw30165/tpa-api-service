package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DownstreamErrorClassificationMappingProperties {
    private String downstreamErrorCode;
    private String eventType;
    private String logLevel;
    private String responseCode;
    private Integer httpStatus;
    private String target;
    private Boolean idempotencyCacheable;
    private Integer cacheTtlSeconds;
}
