package com.bct.ngtpa.apiservice.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.*;
import com.bct.ngtpa.apiservice.application.port.out.AccountBalanceDataPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class GetAccountBalanceServiceTest {
  @Test
  void resolvesReferenceDateOnceThenMapsTrustedFieldsWithoutLosingLeadingZeros() {
    AtomicInteger references = new AtomicInteger();
    AtomicReference<String> resolvedEnv = new AtomicReference<>();
    AtomicReference<FetchAccountBalanceCommand> fetched = new AtomicReference<>();
    ReferenceDatePort referenceDatePort = env -> {
      references.incrementAndGet();
      resolvedEnv.set(env);
      return Mono.just(LocalDate.of(2026, 8, 28));
    };
    AccountBalanceResult expected = result();
    AccountBalanceDataPort dataPort = command -> { fetched.set(command); return Mono.just(expected); };
    GetAccountBalanceService service = new GetAccountBalanceService(referenceDatePort, dataPort);

    StepVerifier.create(service.get(command("USD"))).expectNext(expected).verifyComplete();

    assertThat(references).hasValue(1);
    assertThat(resolvedEnv).hasValue("JP");
    assertThat(fetched.get()).isEqualTo(new FetchAccountBalanceCommand(
        "en", "USD", "JP", "00000008802", "0001", "TRUST", "SCHEME",
        "user-1", ActorUserRole.ROLE_USER_MBR, LocalDate.of(2026, 8, 28)));
  }

  @Test
  void preservesOptionalCurrencyAsNull() {
    AtomicReference<FetchAccountBalanceCommand> fetched = new AtomicReference<>();
    GetAccountBalanceService service = new GetAccountBalanceService(
        env -> Mono.just(LocalDate.of(2026, 8, 28)),
        command -> { fetched.set(command); return Mono.just(result()); });

    StepVerifier.create(service.get(command(null))).expectNextCount(1).verifyComplete();
    assertThat(fetched.get().currency()).isNull();
  }

  @Test
  void referenceDateFailureShortCircuitsDownstreamCall() {
    AtomicInteger downstreamCalls = new AtomicInteger();
    RuntimeException failure = new RuntimeException("reference unavailable");
    GetAccountBalanceService service = new GetAccountBalanceService(
        env -> Mono.error(failure),
        command -> { downstreamCalls.incrementAndGet(); return Mono.just(result()); });

    StepVerifier.create(service.get(command(null))).expectErrorSatisfies(error -> assertThat(error).isSameAs(failure)).verify();
    assertThat(downstreamCalls).hasValue(0);
  }

  @Test
  void emptyReferenceDateShortCircuitsDownstreamCall() {
    AtomicInteger downstreamCalls = new AtomicInteger();
    GetAccountBalanceService service = new GetAccountBalanceService(
        env -> Mono.empty(),
        command -> { downstreamCalls.incrementAndGet(); return Mono.just(result()); });

    StepVerifier.create(service.get(command(null))).verifyComplete();
    assertThat(downstreamCalls).hasValue(0);
  }

  private static GetAccountBalanceCommand command(String currency) {
    return new GetAccountBalanceCommand("en", currency, "JP", "00000008802", "0001",
        "TRUST", "SCHEME", "user-1", ActorUserRole.ROLE_USER_MBR);
  }

  private static AccountBalanceResult result() {
    return new AccountBalanceResult("28/08/2026", new CurrencySelection("HKD", List.of("HKD")),
        new AccountOverview("Display Account", null, null), List.of());
  }
}
