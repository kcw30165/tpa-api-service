package com.bct.ngtpa.apiservice.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.*;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.RawAccountBalanceData;
import com.bct.ngtpa.apiservice.application.dto.metadata.*;
import com.bct.ngtpa.apiservice.application.port.in.AccountBalanceAssembler;
import com.bct.ngtpa.apiservice.application.port.in.GeneralMetadataResolver;
import com.bct.ngtpa.apiservice.application.port.out.*;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AccountBalanceBoundaryIntegrationTest {
  @Test
  void resolvesReferenceMetadataSourceAndAssemblerExactlyOnce() {
    var referenceCalls = new AtomicInteger();
    var metadataCalls = new AtomicInteger();
    var sourceCalls = new AtomicInteger();
    var assemblerCalls = new AtomicInteger();
    var seenQuery = new AtomicReference<ResolveGeneralMetadataQuery>();
    var seenFetch = new AtomicReference<FetchAccountBalanceCommand>();
    ReferenceDatePort dates = env -> { referenceCalls.incrementAndGet(); return Mono.just(LocalDate.of(2026, 8, 28)); };
    GeneralMetadataResolver metadata = query -> { metadataCalls.incrementAndGet(); seenQuery.set(query); return Mono.just(metadata(query)); };
    RawAccountBalanceData raw = new RawAccountBalanceData(null, null, List.of(), List.of(), List.of());
    AccountBalanceSourcePort source = fetch -> { sourceCalls.incrementAndGet(); seenFetch.set(fetch); return Mono.just(raw); };
    AccountBalanceResult expected = new AccountBalanceResult("28/08/2026", null, null, List.of());
    AccountBalanceAssembler assembler = (command, date, resolved, data) -> { assemblerCalls.incrementAndGet(); return Mono.just(expected); };
    var service = new GetAccountBalanceService(dates, metadata, source, assembler);
    StepVerifier.create(service.get(command(null))).expectNext(expected).verifyComplete();
    assertThat(referenceCalls).hasValue(1); assertThat(metadataCalls).hasValue(1);
    assertThat(sourceCalls).hasValue(1); assertThat(assemblerCalls).hasValue(1);
    assertThat(seenQuery.get().scope()).isEqualTo(new GeneralMetadataScope("JP", "TRUST", "00000008802"));
    assertThat(seenQuery.get().requiredKeys()).hasSize(8);
    assertThat(seenQuery.get().actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
    assertThat(seenFetch.get()).isEqualTo(new FetchAccountBalanceCommand("en", null, "JP", "00000008802", "0001", "TRUST", "SCHEME", "user-1", ActorUserRole.ROLE_USER_MBR, LocalDate.of(2026, 8, 28)));
  }

  @Test
  void metadataFailurePreventsSourceAndAssembly() {
    var sourceCalls = new AtomicInteger(); var assemblerCalls = new AtomicInteger();
    RuntimeException failure = new RuntimeException("metadata unavailable");
    var service = new GetAccountBalanceService(env -> Mono.just(LocalDate.of(2026, 8, 28)), q -> Mono.error(failure),
        f -> { sourceCalls.incrementAndGet(); return Mono.empty(); },
        (c,d,m,r) -> { assemblerCalls.incrementAndGet(); return Mono.empty(); });
    StepVerifier.create(service.get(command("USD"))).expectErrorSatisfies(e -> assertThat(e).isSameAs(failure)).verify();
    assertThat(sourceCalls).hasValue(0); assertThat(assemblerCalls).hasValue(0);
  }

  @Test
  void sourceFailurePreventsAssembly() {
    var assemblerCalls = new AtomicInteger(); RuntimeException failure = new RuntimeException("source unavailable");
    GeneralMetadataResolver metadata = q -> Mono.just(metadata(q));
    var service = new GetAccountBalanceService(env -> Mono.just(LocalDate.of(2026, 8, 28)), metadata,
        f -> Mono.error(failure), (c,d,m,r) -> { assemblerCalls.incrementAndGet(); return Mono.empty(); });
    StepVerifier.create(service.get(command("USD"))).expectErrorSatisfies(e -> assertThat(e).isSameAs(failure)).verify();
    assertThat(assemblerCalls).hasValue(0);
  }

  private static GetAccountBalanceCommand command(String currency) { return new GetAccountBalanceCommand("en", currency, "JP", "00000008802", "0001", "TRUST", "SCHEME", "user-1", ActorUserRole.ROLE_USER_MBR); }
  private static ResolvedGeneralMetadataSet metadata(ResolveGeneralMetadataQuery q) { return new ResolvedGeneralMetadataSet(q.scope(), GeneralMetadataResolutionSource.POLICY_SPECIFIC, q.requiredKeys().stream().map(k -> new ResolvedGeneralMetadata(k, k.type() == GeneralMetadataType.PRECISION ? "2" : "HALF_UP")).toList()); }
}
