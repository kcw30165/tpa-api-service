package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;

public interface FailureEventPort {
  void record(FailureEvent event);

  default void record(FailureEvent event, Throwable cause) {
    record(event);
  }
}
