package com.bct.ngtpa.apiservice.config.fundfactsheet;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.GetFundFactSheetCommand;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.RawFundFactSheetData;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.RawFundFactSheetRow;
import com.bct.ngtpa.apiservice.application.port.in.GetFundFactSheetUseCase;
import com.bct.ngtpa.apiservice.application.port.out.FundFactSheetPresentationEventPort;
import com.bct.ngtpa.apiservice.application.usecase.fundfactsheet.FundFactSheetNormalizer;
import com.bct.ngtpa.apiservice.application.usecase.fundfactsheet.GetFundFactSheetService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/** Wiring tests that call the bean methods directly, without a Spring context. */
class FundFactSheetApplicationConfigTest {

  private final FundFactSheetApplicationConfig config = new FundFactSheetApplicationConfig();
  private final List<String> recorded = new ArrayList<>();

  private final FundFactSheetPresentationEventPort events =
      new FundFactSheetPresentationEventPort() {
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
        }
      };

  @Test
  void theNormalizerBeanReportsMalformedLinksThroughTheFeatureEventPort() {
    FundFactSheetNormalizer normalizer = config.fundFactSheetNormalizer(events);
    var raw =
        new RawFundFactSheetData(
            List.of(new RawFundFactSheetRow(1, "Fund", "", "/relative", "https://example.test/z")));

    var result = normalizer.normalize(raw);

    assertThat(result.rows().get(0).linkEng()).isNull();
    assertThat(result.rows().get(0).linkChi()).isEqualTo("https://example.test/z");
    assertThat(recorded).containsExactly("malformedFactsheet");
  }

  @Test
  void theNormalizerBeanUsesTheSameEventPortForTheOtherEvents() {
    FundFactSheetNormalizer normalizer = config.fundFactSheetNormalizer(events);
    var raw =
        new RawFundFactSheetData(
            List.of(
                new RawFundFactSheetRow(null, "A", "", "", ""),
                new RawFundFactSheetRow(2, "", "", "", "")));

    normalizer.normalize(raw);

    assertThat(recorded).containsExactly("missingSeq", "blankEnglishName");
  }

  @Test
  void theUseCaseBeanIsTheServiceAndRunsEndToEnd() {
    FundFactSheetNormalizer normalizer = config.fundFactSheetNormalizer(events);
    GetFundFactSheetUseCase useCase =
        config.getFundFactSheetUseCase(
            env -> Mono.just(LocalDate.of(2026, 5, 5)),
            cmd ->
                Mono.just(
                    new RawFundFactSheetData(
                        List.of(new RawFundFactSheetRow(9, "Fund", "", "", "")))),
            normalizer);

    var result =
        useCase
            .get(new GetFundFactSheetCommand("JP", "00000008802", ActorUserRole.ROLE_USER_MBR))
            .block();

    assertThat(useCase).isInstanceOf(GetFundFactSheetService.class);
    assertThat(result.rows()).hasSize(1);
    assertThat(result.rows().get(0).seq()).isEqualTo(9);
  }

  @Test
  void theConfigurationClassIsUnproxiedAndDeclaresExactlyTwoBeans() {
    Configuration configuration =
        FundFactSheetApplicationConfig.class.getAnnotation(Configuration.class);

    assertThat(configuration).isNotNull();
    assertThat(configuration.proxyBeanMethods()).isFalse();
    long beans =
        Arrays.stream(FundFactSheetApplicationConfig.class.getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(Bean.class))
            .count();
    assertThat(beans).isEqualTo(2L);
  }
}
