package com.bct.ngtpa.apiservice.application.usecase.fundfactsheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.FetchFundFactSheetCommand;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.FundFactSheetRowResult;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.GetFundFactSheetCommand;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.RawFundFactSheetData;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.RawFundFactSheetRow;
import com.bct.ngtpa.apiservice.application.exception.FundFactSheetRetrievalException;
import com.bct.ngtpa.apiservice.application.port.out.FundFactSheetPresentationEventPort;
import com.bct.ngtpa.apiservice.application.port.out.FundFactSheetSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.service.presentation.UrlValidator;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class GetFundFactSheetServiceTest {

  private static final LocalDate DATE = LocalDate.of(2026, 5, 5);

  private final AtomicInteger dateCalls = new AtomicInteger();
  private final AtomicInteger sourceCalls = new AtomicInteger();
  private final AtomicReference<String> envSeen = new AtomicReference<>();
  private final AtomicReference<FetchFundFactSheetCommand> fetched = new AtomicReference<>();
  private final AtomicInteger duplicateEvents = new AtomicInteger();

  private final FundFactSheetPresentationEventPort events =
      new FundFactSheetPresentationEventPort() {
        @Override
        public void missingSeq() {}

        @Override
        public void duplicateSeq() {
          duplicateEvents.incrementAndGet();
        }

        @Override
        public void blankEnglishName() {}

        @Override
        public void malformedFactsheet() {
          throw new IllegalStateException("boom from the event port");
        }
      };

  private final FundFactSheetNormalizer normalizer =
      new FundFactSheetNormalizer(new UrlValidator(events::malformedFactsheet), events);

  private static GetFundFactSheetCommand command() {
    return new GetFundFactSheetCommand("JP", "00000008802", ActorUserRole.ROLE_USER_MBR);
  }

  private static RawFundFactSheetRow row(Integer seq, String link) {
    return new RawFundFactSheetRow(seq, "Fund " + seq, "", link, "");
  }

  private ReferenceDatePort date() {
    return env -> {
      dateCalls.incrementAndGet();
      envSeen.set(env);
      return Mono.just(DATE);
    };
  }

  private FundFactSheetSourcePort source(RawFundFactSheetData raw) {
    return cmd -> {
      sourceCalls.incrementAndGet();
      fetched.set(cmd);
      return Mono.just(raw);
    };
  }

  private GetFundFactSheetService service(ReferenceDatePort date, FundFactSheetSourcePort source) {
    return new GetFundFactSheetService(date, source, normalizer);
  }

  @Test
  void buildsTheFetchCommandFromTheCommandAndTheResolvedReferenceDate() {
    var raw = new RawFundFactSheetData(List.of(row(1, "")));

    StepVerifier.create(service(date(), source(raw)).get(command()))
        .assertNext(result -> assertThat(result.rows()).hasSize(1))
        .verifyComplete();

    assertThat(envSeen.get()).isEqualTo("JP");
    assertThat(fetched.get())
        .isEqualTo(
            new FetchFundFactSheetCommand("00000008802", DATE, "JP", ActorUserRole.ROLE_USER_MBR));
    assertThat(dateCalls).hasValue(1);
    assertThat(sourceCalls).hasValue(1);
  }

  @Test
  void normalizesTheRawRows() {
    var raw = new RawFundFactSheetData(List.of(row(2, ""), row(1, ""), row(2, "")));

    StepVerifier.create(service(date(), source(raw)).get(command()))
        .assertNext(
            result ->
                assertThat(result.rows())
                    .extracting(FundFactSheetRowResult::seq)
                    .containsExactly(2, 1))
        .verifyComplete();

    assertThat(duplicateEvents).hasValue(1);
  }

  @Test
  void aNoFundSourceResultGivesAnEmptyResult() {
    var raw = new RawFundFactSheetData(List.of());

    StepVerifier.create(service(date(), source(raw)).get(command()))
        .assertNext(result -> assertThat(result.rows()).isEmpty())
        .verifyComplete();
  }

  @Test
  void aReferenceDateFailureBecomesTheRetrievalFailureAndTheSourceIsNotCalled() {
    ReferenceDatePort failing = env -> Mono.error(new IllegalStateException("redis down"));

    var service = service(failing, source(new RawFundFactSheetData(List.of())));

    StepVerifier.create(service.get(command()))
        .expectErrorSatisfies(this::assertRetrievalFailure)
        .verify();

    assertThat(sourceCalls).hasValue(0);
  }

  @Test
  void aSourceFailureBecomesTheRetrievalFailure() {
    FundFactSheetSourcePort failing = cmd -> Mono.error(new IllegalStateException("boom"));

    StepVerifier.create(service(date(), failing).get(command()))
        .expectErrorSatisfies(this::assertRetrievalFailure)
        .verify();
  }

  @Test
  void aRetrievalFailureFromTheSourcePassesThroughUnchanged() {
    var original = FundFactSheetRetrievalException.systemFailure();
    FundFactSheetSourcePort failing = cmd -> Mono.error(original);

    StepVerifier.create(service(date(), failing).get(command()))
        .expectErrorSatisfies(error -> assertThat(error).isSameAs(original))
        .verify();
  }

  @Test
  void aRetrievalFailureFromTheReferenceDatePassesThroughUnchanged() {
    var original = FundFactSheetRetrievalException.systemFailure();
    ReferenceDatePort failing = env -> Mono.error(original);

    var service = service(failing, source(new RawFundFactSheetData(List.of())));

    StepVerifier.create(service.get(command()))
        .expectErrorSatisfies(error -> assertThat(error).isSameAs(original))
        .verify();
  }

  @Test
  void aReferenceDateThatCompletesWithoutAValueFailsClosed() {
    ReferenceDatePort empty = env -> Mono.empty();

    StepVerifier.create(service(empty, source(new RawFundFactSheetData(List.of()))).get(command()))
        .expectErrorSatisfies(this::assertRetrievalFailure)
        .verify();

    assertThat(sourceCalls).hasValue(0);
  }

  @Test
  void aSourceThatCompletesWithoutAValueFailsClosed() {
    FundFactSheetSourcePort empty = cmd -> Mono.empty();

    StepVerifier.create(service(date(), empty).get(command()))
        .expectErrorSatisfies(this::assertRetrievalFailure)
        .verify();
  }

  @Test
  void aNormalizerFailureBecomesTheRetrievalFailure() {
    var raw = new RawFundFactSheetData(List.of(row(1, "/relative")));

    StepVerifier.create(service(date(), source(raw)).get(command()))
        .expectErrorSatisfies(this::assertRetrievalFailure)
        .verify();
  }

  @Test
  void aBlankPolicyNumberFailsInsideTheChainAndTheSourceIsNotCalled() {
    var blank = new GetFundFactSheetCommand("JP", "  ", ActorUserRole.ROLE_USER_MBR);

    StepVerifier.create(service(date(), source(new RawFundFactSheetData(List.of()))).get(blank))
        .expectErrorSatisfies(this::assertRetrievalFailure)
        .verify();

    assertThat(dateCalls).hasValue(1);
    assertThat(sourceCalls).hasValue(0);
  }

  @Test
  void aMissingAccountEnvironmentFailsInsideTheChainAndTheSourceIsNotCalled() {
    var missing = new GetFundFactSheetCommand(null, "00000008802", ActorUserRole.ROLE_USER_MBR);

    StepVerifier.create(service(date(), source(new RawFundFactSheetData(List.of()))).get(missing))
        .expectErrorSatisfies(this::assertRetrievalFailure)
        .verify();

    assertThat(sourceCalls).hasValue(0);
  }

  @Test
  void nothingHappensBeforeSubscriptionAndEverySubscriptionRunsTheChainAgain() {
    var service = service(date(), source(new RawFundFactSheetData(List.of())));

    Mono<?> pipeline = service.get(command());
    assertThat(dateCalls).hasValue(0);
    assertThat(sourceCalls).hasValue(0);

    pipeline.block();
    pipeline.block();
    assertThat(dateCalls).hasValue(2);
    assertThat(sourceCalls).hasValue(2);
  }

  @Test
  void constructorAndCommandRequireNonNull() {
    var date = date();
    var source = source(new RawFundFactSheetData(List.of()));

    assertThatThrownBy(() -> new GetFundFactSheetService(null, source, normalizer))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("referenceDatePort");
    assertThatThrownBy(() -> new GetFundFactSheetService(date, null, normalizer))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("source");
    assertThatThrownBy(() -> new GetFundFactSheetService(date, source, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("normalizer");
    assertThatThrownBy(() -> service(date, source).get(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("command");
  }

  private void assertRetrievalFailure(Throwable error) {
    assertThat(error).isInstanceOf(FundFactSheetRetrievalException.class);
    assertThat(error.getMessage()).isEqualTo("Fund fact sheet retrieval failed");
    assertThat(error.getCause()).isNull();
  }
}
