package com.bct.ngtpa.apiservice.application.port.out;

/** Framework-neutral JSON boundary for transient shared-execution payloads. */
public interface TransientJsonPort {
  String write(Object value);

  <T> T read(String json, Class<T> type);
}
