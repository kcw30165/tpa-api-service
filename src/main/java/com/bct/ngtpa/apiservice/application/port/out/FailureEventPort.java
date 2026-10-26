package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;

public interface FailureEventPort {
  void publish(FailureEvent event);

  default void publish(FailureEvent event, Throwable cause) {
    publish(event);
  }
}
