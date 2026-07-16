package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;

public interface FailureEventPort {
    void record(FailureEvent event);
}
