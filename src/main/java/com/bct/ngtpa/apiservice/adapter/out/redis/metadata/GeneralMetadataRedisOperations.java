package com.bct.ngtpa.apiservice.adapter.out.redis.metadata;

import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataCacheReplaceResult;
import reactor.core.publisher.Mono;

interface GeneralMetadataRedisOperations {
  Mono<GeneralMetadataCacheReplaceResult> replaceIfUnchanged(
      String key, String expected, String replacement);
}
