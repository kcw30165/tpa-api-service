package com.bct.ngtpa.apiservice.adapter.out.referencedate;

import static org.assertj.core.api.Assertions.assertThat;
import com.bct.ngtpa.apiservice.adapter.out.configserver.ReferenceDateProperties;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.RefreshReferenceDateCommand;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.application.usecase.RefreshReferenceDateService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ReferenceDateRefreshReadConsistencyTest {
 @Test void manualRefreshThenNormalReadUsesSameQualifiedRedisEntry() {
  Map<String,String> store=new HashMap<>();
  var service=new RefreshReferenceDateService(command -> Mono.just(LocalDate.of(2026,6,30)),
      command -> { store.put(command.cacheKey(),command.cacheValue()); return Mono.empty(); },"ngtpa");
  CachePort cache=new CachePort(){ public Mono<Optional<String>> get(String k){return Mono.just(Optional.ofNullable(store.get(k)));} public Mono<Void> set(String k,String v,Duration t){return Mono.empty();} public Mono<Boolean> evict(String k){return Mono.just(false);} };
  var env=new MockEnvironment(); env.setActiveProfiles("SIT");
  var reader=new ReferenceDateAdapter(new ReferenceDateProperties(),Optional.of(cache),"ngtpa",env,Clock.systemUTC());
  StepVerifier.create(service.execute(new RefreshReferenceDateCommand("JP",ActorUserRole.ROLE_USER_MBR)).then(reader.resolveReferenceDate("JP")))
      .assertNext(date -> assertThat(date).isEqualTo(LocalDate.of(2026,6,30))).verifyComplete();
  assertThat(store).containsEntry("ngtpa:reference-date:JP","30/06/2026");
 }
}
