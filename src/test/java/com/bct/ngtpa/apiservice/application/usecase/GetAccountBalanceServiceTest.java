package com.bct.ngtpa.apiservice.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.AccountBalanceResult;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.FetchAccountBalanceCommand;
import com.bct.ngtpa.apiservice.application.dto.GetAccountBalanceCommand;
import com.bct.ngtpa.apiservice.application.dto.InvestmentPortfolio;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.RawAccountBalanceData;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataResolutionSource;
import com.bct.ngtpa.apiservice.application.dto.metadata.ResolvedGeneralMetadata;
import com.bct.ngtpa.apiservice.application.dto.metadata.ResolvedGeneralMetadataSet;
import com.bct.ngtpa.apiservice.application.port.in.AccountBalanceAssembler;
import com.bct.ngtpa.apiservice.application.port.in.GeneralMetadataResolver;
import com.bct.ngtpa.apiservice.application.port.out.AccountBalanceSourcePort;
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
  void preservesTrustedFieldsLeadingZerosAndOptionalCurrencyAcrossNewBoundary() {
    var references = new AtomicInteger();
    var metadataCalls = new AtomicInteger();
    var fetched = new AtomicReference<FetchAccountBalanceCommand>();
    var assembled = new AtomicInteger();
    ReferenceDatePort dates =
        env -> {
          references.incrementAndGet();
          return Mono.just(LocalDate.of(2026, 8, 28));
        };
    GeneralMetadataResolver metadata =
        query -> {
          metadataCalls.incrementAndGet();
          return Mono.just(
              new ResolvedGeneralMetadataSet(
                  query.scope(),
                  GeneralMetadataResolutionSource.POLICY_SPECIFIC,
                  query.requiredKeys().stream()
                      .map(
                          key ->
                              new ResolvedGeneralMetadata(
                                  key, key.type().name().equals("PRECISION") ? "2" : "HALF_UP"))
                      .toList()));
        };
    RawAccountBalanceData raw =
        new RawAccountBalanceData(null, null, List.of(), List.of(), List.of());
    AccountBalanceSourcePort source =
        command -> {
          fetched.set(command);
          return Mono.just(raw);
        };
    AccountBalanceResult expected = new AccountBalanceResult("28/08/2026", null, null, new InvestmentPortfolio(List.of(), List.of()));
    AccountBalanceAssembler assembler =
        (command, date, resolved, data) -> {
          assembled.incrementAndGet();
          assertThat(command.currency()).isNull();
          assertThat(date).isEqualTo(LocalDate.of(2026, 8, 28));
          assertThat(data).isSameAs(raw);
          return Mono.just(expected);
        };

    var service = new GetAccountBalanceService(dates, metadata, source, assembler);
    StepVerifier.create(service.get(command(null))).expectNext(expected).verifyComplete();

    assertThat(references).hasValue(1);
    assertThat(metadataCalls).hasValue(1);
    assertThat(assembled).hasValue(1);
    assertThat(fetched.get())
        .isEqualTo(
            new FetchAccountBalanceCommand(
                "en",
                null,
                "JP",
                "00000008802",
                "0001",
                "TRUST",
                "SCHEME",
                "user-1",
                ActorUserRole.ROLE_USER_MBR,
                LocalDate.of(2026, 8, 28)));
  }

  @Test
  void emptyReferenceDateShortCircuitsEveryLaterStage() {
    var metadataCalls = new AtomicInteger();
    var sourceCalls = new AtomicInteger();
    var assemblerCalls = new AtomicInteger();
    var service =
        new GetAccountBalanceService(
            env -> Mono.empty(),
            query -> {
              metadataCalls.incrementAndGet();
              return Mono.empty();
            },
            fetch -> {
              sourceCalls.incrementAndGet();
              return Mono.empty();
            },
            (command, date, metadata, raw) -> {
              assemblerCalls.incrementAndGet();
              return Mono.empty();
            });

    StepVerifier.create(service.get(command("USD"))).verifyComplete();
    assertThat(metadataCalls).hasValue(0);
    assertThat(sourceCalls).hasValue(0);
    assertThat(assemblerCalls).hasValue(0);
  }

  @Test
  void referenceDateFailureShortCircuitsEveryLaterStage() {
    var laterCalls = new AtomicInteger();
    var failure = new RuntimeException("reference unavailable");
    var service =
        new GetAccountBalanceService(
            env -> Mono.error(failure),
            query -> {
              laterCalls.incrementAndGet();
              return Mono.empty();
            },
            fetch -> {
              laterCalls.incrementAndGet();
              return Mono.empty();
            },
            (command, date, metadata, raw) -> {
              laterCalls.incrementAndGet();
              return Mono.empty();
            });

    StepVerifier.create(service.get(command(null)))
        .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
        .verify();
    assertThat(laterCalls).hasValue(0);
  }

  private static GetAccountBalanceCommand command(String currency) {
    return new GetAccountBalanceCommand(
        "en",
        currency,
        "JP",
        "00000008802",
        "0001",
        "TRUST",
        "SCHEME",
        "user-1",
        ActorUserRole.ROLE_USER_MBR);
  }
}
