package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.downstream.DownstreamErrorClassification;
import com.bct.ngtpa.apiservice.application.dto.downstream.DownstreamErrorClassificationRequest;

public interface DownstreamErrorClassificationPort {
    DownstreamErrorClassification classify(DownstreamErrorClassificationRequest request);
}
