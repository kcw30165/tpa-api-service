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
import com.bct.ngtpa.apiservice.application.service.presentation.UrlValidator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class GetFundFactSheetServiceTest {

  private static final ActorUserRole ROLE = ActorUserRole.ROLE_USER_MBR;
  private static final List<String> ABSENT = Arrays.asList(null, "", "   ");

  private final AtomicInteger sourceCalls = new AtomicInteger();
  private final AtomicReference<FetchFundFactSheetCommand> fetched = new AtomicReference<>();
  private final RecordingEvents events = new RecordingEvents();

  private final FundFactSheetNormalizer normalizer =
      new FundFactSheetNormalizer(new UrlValidator(events::malformedFactsheet), events);

  private static GetFundFactSheetCommand command() {
    return new GetFundFactSheetCommand("JP", "00000008802", "0230", "OE", ROLE);
  }

  private static GetFundFactSheetCommand withIdentifiers(
      String policyNo, String certNo, String schemeType) {
    return new GetFundFactSheetCommand("JP", policyNo, certNo, schemeType, ROLE);
  }

  private static RawFundFactSheetData emptyData() {
    return new RawFundFactSheetData(List.of());
  }

  private static RawFundFactSheetRow row(Integer seq, String link) {
    return new RawFundFactSheetRow(seq, "Fund " + seq, "", link, "");
  }

  private FundFactSheetSourcePort source(RawFundFactSheetData raw) {
    return cmd -> {
      sourceCalls.incrementAndGet();
      fetched.set(cmd);
      return Mono.just(raw);
    };
  }

  private GetFundFactSheetService service(FundFactSheetSourcePort source) {
    return new GetFundFactSheetService(source, normalizer, events);
  }

  @Test
  void buildsTheFetchCommandFromTheCommandExactlyAsGiven() {
    var raw = new RawFundFactSheetData(List.of(row(1, "")));

    StepVerifier.create(service(source(raw)).get(command()))
        .assertNext(result -> assertThat(result.rows()).hasSize(1))
        .verifyComplete();

    assertThat(fetched.get())
        .isEqualTo(new FetchFundFactSheetCommand("00000008802", "0230", "OE", "JP", ROLE));
    assertThat(sourceCalls).hasValue(1);
    assertThat(events.recorded()).isEmpty();
  }

  @Test
  void certNoAndSchemeTypeAreNotTrimmedOrConverted() {
    var command = withIdentifiers("00000008802", " 0230 ", "oe");

    StepVerifier.create(service(source(emptyData())).get(command))
        .assertNext(result -> assertThat(result.rows()).isEmpty())
        .verifyComplete();

    assertThat(fetched.get().certNo()).isEqualTo(" 0230 ");
    assertThat(fetched.get().schemeType()).isEqualTo("oe");
  }

  @Test
  void normalizesTheRawRows() {
    var raw = new RawFundFactSheetData(List.of(row(2, ""), row(1, ""), row(2, "")));

    StepVerifier.create(service(source(raw)).get(command()))
        .assertNext(
            result ->
                assertThat(result.rows())
                    .extracting(FundFactSheetRowResult::seq)
                    .containsExactly(2, 1))
        .verifyComplete();

    assertThat(events.recorded()).containsExactly("duplicateSeq");
  }

  @Test
  void aNoFundSourceResultGivesAnEmptyResult() {
    StepVerifier.create(service(source(emptyData())).get(command()))
        .assertNext(result -> assertThat(result.rows()).isEmpty())
        .verifyComplete();
  }

  @Test
  void anAbsentPolicyNoFailsWithOneEventAndTheSourceIsNotCalled() {
    for (String absent : ABSENT) {
      assertAbsentIdentifier(withIdentifiers(absent, "0230", "OE"));
    }
  }

  @Test
  void anAbsentCertNoFailsWithOneEventAndTheSourceIsNotCalled() {
    for (String absent : ABSENT) {
      assertAbsentIdentifier(withIdentifiers("00000008802", absent, "OE"));
    }
  }

  @Test
  void anAbsentSchemeTypeFailsWithOneEventAndTheSourceIsNotCalled() {
    for (String absent : ABSENT) {
      assertAbsentIdentifier(withIdentifiers("00000008802", "0230", absent));
    }
  }

  @Test
  void severalAbsentIdentifiersAreReportedOnce() {
    assertAbsentIdentifier(withIdentifiers(null, "", "  "));
  }

  @Test
  void anAbsentAccountEnvironmentOrRoleFailsInsideTheChainWithoutAnEventAndWithoutACall() {
    var noEnv = new GetFundFactSheetCommand(null, "00000008802", "0230", "OE", ROLE);
    var blankEnv = new GetFundFactSheetCommand("  ", "00000008802", "0230", "OE", ROLE);
    var noRole = new GetFundFactSheetCommand("JP", "00000008802", "0230", "OE", null);

    for (var command : List.of(noEnv, blankEnv, noRole)) {
      StepVerifier.create(service(source(emptyData())).get(command))
          .expectErrorSatisfies(this::assertRetrievalFailure)
          .verify();
    }

    assertThat(events.recorded()).isEmpty();
    assertThat(sourceCalls).hasValue(0);
  }

  @Test
  void aThrowingEventPortStillGivesTheRetrievalFailure() {
    var throwing =
        new RecordingEvents() {
          @Override
          public void missingAccountIdentifier() {
            super.missingAccountIdentifier();
            throw new IllegalStateException("boom from the event port");
          }
        };
    var service = new GetFundFactSheetService(source(emptyData()), normalizer, throwing);

    StepVerifier.create(service.get(withIdentifiers(null, "0230", "OE")))
        .expectErrorSatisfies(this::assertRetrievalFailure)
        .verify();

    assertThat(throwing.recorded()).containsExactly("missingAccountIdentifier");
    assertThat(sourceCalls).hasValue(0);
  }

  @Test
  void aSourceFailureBecomesTheRetrievalFailure() {
    FundFactSheetSourcePort failing = cmd -> Mono.error(new IllegalStateException("boom"));

    StepVerifier.create(service(failing).get(command()))
        .expectErrorSatisfies(this::assertRetrievalFailure)
        .verify();
  }

  @Test
  void aRetrievalFailureFromTheSourcePassesThroughUnchanged() {
    var original = FundFactSheetRetrievalException.systemFailure();
    FundFactSheetSourcePort failing = cmd -> Mono.error(original);

    StepVerifier.create(service(failing).get(command()))
        .expectErrorSatisfies(error -> assertThat(error).isSameAs(original))
        .verify();
  }

  @Test
  void aSourceThatCompletesWithoutAValueFailsClosed() {
    FundFactSheetSourcePort empty = cmd -> Mono.empty();

    StepVerifier.create(service(empty).get(command()))
        .expectErrorSatisfies(this::assertRetrievalFailure)
        .verify();
  }

  @Test
  void aNormalizerFailureBecomesTheRetrievalFailure() {
    var raw = new RawFundFactSheetData(List.of(row(1, "/relative")));

    StepVerifier.create(service(source(raw)).get(command()))
        .expectErrorSatisfies(this::assertRetrievalFailure)
        .verify();
  }

  @Test
  void nothingHappensBeforeSubscriptionAndEverySubscriptionRunsTheChainAgain() {
    var service = service(source(emptyData()));

    Mono<?> pipeline = service.get(command());
    assertThat(sourceCalls).hasValue(0);

    pipeline.block();
    pipeline.block();
    assertThat(sourceCalls).hasValue(2);
  }

  @Test
  void constructorAndCommandRequireNonNull() {
    var source = source(emptyData());

    assertThatThrownBy(() -> new GetFundFactSheetService(null, normalizer, events))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("source");
    assertThatThrownBy(() -> new GetFundFactSheetService(source, null, events))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("normalizer");
    assertThatThrownBy(() -> new GetFundFactSheetService(source, normalizer, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("events");
    assertThatThrownBy(() -> service(source).get(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("command");
  }

  private void assertAbsentIdentifier(GetFundFactSheetCommand command) {
    events.clear();
    sourceCalls.set(0);

    StepVerifier.create(service(source(emptyData())).get(command))
        .expectErrorSatisfies(this::assertRetrievalFailure)
        .verify();

    assertThat(events.recorded()).containsExactly("missingAccountIdentifier");
    assertThat(sourceCalls).hasValue(0);
  }

  private void assertRetrievalFailure(Throwable error) {
    assertThat(error).isInstanceOf(FundFactSheetRetrievalException.class);
    assertThat(error.getMessage()).isEqualTo("Fund fact sheet retrieval failed");
    assertThat(error.getCause()).isNull();
  }

  /** Records every reported event; a malformed link also throws, to exercise the error mapping. */
  private static class RecordingEvents implements FundFactSheetPresentationEventPort {
    private final List<String> recorded = new ArrayList<>();

    @Override
    public void missingSeq() {
      recorded.add("missingSeq");
    }

    @Override
    public void duplicateSeq() {
      recorded.add("duplicateSeq");
    }

    @Override
    public void blankEnglishName() {
      recorded.add("blankEnglishName");
    }

    @Override
    public void malformedFactsheet() {
      recorded.add("malformedFactsheet");
      throw new IllegalStateException("boom from the event port");
    }

    @Override
    public void missingAccountIdentifier() {
      recorded.add("missingAccountIdentifier");
    }

    List<String> recorded() {
      return recorded;
    }

    void clear() {
      recorded.clear();
    }
  }
}
