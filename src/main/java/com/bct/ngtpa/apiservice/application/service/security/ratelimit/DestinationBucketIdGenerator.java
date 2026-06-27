package com.bct.ngtpa.apiservice.application.service.security.ratelimit;

public interface DestinationBucketIdGenerator {

    String generateBucketId(DestinationType destinationType, String destination);
}
