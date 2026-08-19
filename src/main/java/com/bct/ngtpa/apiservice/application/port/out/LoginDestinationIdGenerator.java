package com.bct.ngtpa.apiservice.application.port.out;

@FunctionalInterface
public interface LoginDestinationIdGenerator {
  String generate();
}
