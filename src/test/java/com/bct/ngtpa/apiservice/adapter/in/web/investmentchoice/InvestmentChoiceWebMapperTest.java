package com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.response.InvestmentChoiceAllocationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.response.InvestmentChoiceDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.response.InvestmentChoiceFundResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.response.InvestmentChoiceHistoryResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.response.InvestmentChoiceNormalDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.response.InvestmentChoicePendingAllocationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.response.InvestmentChoicePendingInstructionResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.response.InvestmentChoiceRestrictedDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.response.InvestmentChoiceSwitchSourceResponse;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceDetails;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceFund;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceMandate;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoicePending;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoicePendingDetail;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceResult;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceScenario;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InvestmentChoiceWebMapperTest {

  private final InvestmentChoiceWebMapper mapper = new InvestmentChoiceWebMapper();

  @Test
  void mapsEveryFieldAndOrdersSourcesFundsHistoryAndPendingInstructions() {
    InvestmentChoiceNormalDataResponse response =
        (InvestmentChoiceNormalDataResponse)
            mapper.toDataResponse(new InvestmentChoiceResult("00000008802-0230", details()), "en");

    assertThat(response.accountNumber()).isEqualTo("00000008802-0230");
    assertThat(new ObjectMapper().valueToTree(response).path("scenario").asText())
        .isEqualTo("NORMAL");
    assertThat(response.intervalPercentage()).isEqualTo(5);
    assertThat(response.switchSources())
        .containsExactly(
            new InvestmentChoiceSwitchSourceResponse("EE", "Employee source", 10, true));
    assertThat(response.funds())
        .containsExactly(
            new InvestmentChoiceFundResponse("F02", "alpha", 20, "1"),
            new InvestmentChoiceFundResponse("F03", "Alpha", 30, "1"),
            new InvestmentChoiceFundResponse("F01", "Zulu", 60, "1"),
            new InvestmentChoiceFundResponse("F04", "Beta", 50, null),
            new InvestmentChoiceFundResponse("F05", "Beta", 40, ""),
            new InvestmentChoiceFundResponse("F06", "Zero", 10, "0"));

    assertThat(response.history())
        .containsExactly(
            new InvestmentChoiceHistoryResponse(
                "01/10/2026",
                List.of(
                    new InvestmentChoiceAllocationResponse(
                        "EE", "Employee source", "F02", "alpha", "05"),
                    new InvestmentChoiceAllocationResponse(
                        "EE", "Employee source", "F07", "Closed Fund", "04"),
                    new InvestmentChoiceAllocationResponse(
                        "EE", "Employee source", "F06", "Zero", "90"),
                    new InvestmentChoiceAllocationResponse(
                        "EE", "Employee source", "UNKNOWN", null, "03"),
                    new InvestmentChoiceAllocationResponse(
                        "BO", "Both source", "F01", "Zulu", "99"))),
            new InvestmentChoiceHistoryResponse(
                "02/01/2025",
                List.of(
                    new InvestmentChoiceAllocationResponse(
                        "EE", "Employee source", "MISSING", null, "12"))));

    assertThat(response.pendingInstructions())
        .containsExactly(
            new InvestmentChoicePendingInstructionResponse(
                "REF-NEW",
                "02/10/2026",
                "14:30:05",
                List.of(
                    new InvestmentChoicePendingAllocationResponse(
                        "EE", "Employee source", "F02", "alpha", 8),
                    new InvestmentChoicePendingAllocationResponse(
                        "EE", "Employee source", "F07", "Closed Fund", 7),
                    new InvestmentChoicePendingAllocationResponse(
                        "BO", "Both source", "F01", "Zulu", 6))),
            new InvestmentChoicePendingInstructionResponse(
                "REF-OLD",
                "03/08/2026",
                "09:00:00",
                List.of(
                    new InvestmentChoicePendingAllocationResponse(
                        "EE", "Employee source", "MISSING", null, 100))));
  }

  @Test
  void zhHkUsesChineseNameAndFallsBackToEnglishWhenChineseNameIsBlank() {
    InvestmentChoiceNormalDataResponse response =
        (InvestmentChoiceNormalDataResponse)
            mapper.toDataResponse(new InvestmentChoiceResult("account", details()), "zh_HK");

    assertThat(response.funds())
        .filteredOn(fund -> fund.fundCode().equals("F02"))
        .extracting(InvestmentChoiceFundResponse::fundName)
        .containsExactly("阿爾法");
    assertThat(response.funds())
        .filteredOn(fund -> fund.fundCode().equals("F03"))
        .extracting(InvestmentChoiceFundResponse::fundName)
        .containsExactly("Alpha");
    assertThat(response.history().getFirst().allocations().get(0).fundName()).isEqualTo("阿爾法");
  }

  @Test
  void allocationRowsKeepSourceCodesAndDescriptionsWhileUnavailableSourcesStayInternal()
      throws Exception {
    var details =
        new InvestmentChoiceDetails.Normal(
            5,
            List.of(
                new InvestmentChoiceSource("SRC-LOCKED", "Locked source", 20, false),
                new InvestmentChoiceSource("SRC-READY", "Ready source", 10, true)),
            List.of(new InvestmentChoiceFund("FUND-KNOWN", "Known fund", null, "1", false)),
            List.of(
                new InvestmentChoiceMandate(
                    LocalDate.of(2026, 10, 1), "SRC-LOCKED", "FUND-KNOWN", "70"),
                new InvestmentChoiceMandate(
                    LocalDate.of(2026, 10, 1), "SRC-MISSING", "FUND-MISSING", "30")),
            List.of(
                new InvestmentChoicePending(
                    "REF-1",
                    LocalDate.of(2026, 10, 2),
                    LocalTime.of(14, 30),
                    List.of(
                        new InvestmentChoicePendingDetail("SRC-LOCKED", "FUND-KNOWN", 70),
                        new InvestmentChoicePendingDetail("SRC-MISSING", "FUND-MISSING", 30)))));

    var json =
        new ObjectMapper()
            .valueToTree(
                mapper.toDataResponse(new InvestmentChoiceResult("account", details), "en"));

    assertThat(json.path("switchSources").size()).isEqualTo(1);
    assertThat(json.path("switchSources").get(0).path("switchSourceCode").asText())
        .isEqualTo("SRC-READY");
    assertThat(
            json.path("history")
                .get(0)
                .path("allocations")
                .get(0)
                .path("switchSourceCode")
                .asText())
        .isEqualTo("SRC-LOCKED");
    assertThat(json.path("history").get(0).path("allocations").get(0).path("switchSource").asText())
        .isEqualTo("Locked source");
    assertThat(
            json.path("history")
                .get(0)
                .path("allocations")
                .get(1)
                .path("switchSourceCode")
                .asText())
        .isEqualTo("SRC-MISSING");
    assertThat(json.path("history").get(0).path("allocations").get(1).path("switchSource").isNull())
        .isTrue();
    assertThat(json.path("history").get(0).path("allocations").get(1).path("fundCode").asText())
        .isEqualTo("FUND-MISSING");
    assertThat(json.path("history").get(0).path("allocations").get(1).path("fundName").isNull())
        .isTrue();
    assertThat(
            json.path("pendingInstructions")
                .get(0)
                .path("allocations")
                .get(0)
                .path("switchSource")
                .asText())
        .isEqualTo("Locked source");
    assertThat(
            json.path("pendingInstructions")
                .get(0)
                .path("allocations")
                .get(1)
                .path("switchSourceCode")
                .asText())
        .isEqualTo("SRC-MISSING");
  }

  @ParameterizedTest
  @ValueSource(strings = {"en", "", "unknown"})
  void nonChineseLanguagesUseEnglishNames(String language) {
    InvestmentChoiceNormalDataResponse response =
        (InvestmentChoiceNormalDataResponse)
            mapper.toDataResponse(new InvestmentChoiceResult("account", details()), language);

    assertThat(response.funds().getFirst().fundName()).isEqualTo("alpha");
  }

  @Test
  void emptyCollectionsRemainEmptyLists() {
    var empty = new InvestmentChoiceDetails.Normal(1, List.of(), List.of(), List.of(), List.of());

    assertThat(mapper.toDataResponse(new InvestmentChoiceResult("account", empty), "en"))
        .isEqualTo(
            new InvestmentChoiceNormalDataResponse(
                "account",
                InvestmentChoiceScenario.NORMAL,
                1,
                List.of(),
                List.of(),
                List.of(),
                List.of()));
  }

  @Test
  void restrictedResponseContainsOnlyAccountScenarioAndHistoryWithDeterministicFallbackOrder() {
    var details =
        new InvestmentChoiceDetails.Restricted(
            InvestmentChoiceScenario.FUNCTION_UNAVAILABLE,
            List.of(),
            List.of(
                new InvestmentChoiceFund("FUND_B", "Beta", null, 2, "1", false),
                new InvestmentChoiceFund("FUND_A", "Alpha", null, 1, "1", true)),
            List.of(
                new InvestmentChoiceMandate(LocalDate.of(2026, 10, 1), "ZZ", "UNKNOWN", "05"),
                new InvestmentChoiceMandate(LocalDate.of(2026, 10, 1), "AA", "FUND_A", "95")));

    InvestmentChoiceDataResponse response =
        mapper.toDataResponse(new InvestmentChoiceResult("account", details), "en");
    var json = new ObjectMapper().valueToTree(response);

    assertThat(response).isInstanceOf(InvestmentChoiceRestrictedDataResponse.class);
    assertThat(json.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder("accountNumber", "scenario", "history");
    assertThat(json.path("scenario").asText()).isEqualTo("FUNCTION_UNAVAILABLE");
    assertThat(response.history().getFirst().allocations())
        .containsExactly(
            new InvestmentChoiceAllocationResponse("AA", null, "FUND_A", "Alpha", "95"),
            new InvestmentChoiceAllocationResponse("ZZ", null, "UNKNOWN", null, "05"));
  }

  private static InvestmentChoiceDetails details() {
    return new InvestmentChoiceDetails.Normal(
        5,
        List.of(
            new InvestmentChoiceSource("BO", "Both source", 20, false),
            new InvestmentChoiceSource("EE", "Employee source", 10, true),
            new InvestmentChoiceSource("AA", "Alpha source", 10, false)),
        List.of(
            new InvestmentChoiceFund("F01", "Zulu", "祖魯", 60, "1", true),
            new InvestmentChoiceFund("F02", "alpha", "阿爾法", 20, "1", true),
            new InvestmentChoiceFund("F03", "Alpha", " ", 30, "1", true),
            new InvestmentChoiceFund("F04", "Beta", null, 50, null, true),
            new InvestmentChoiceFund("F05", "Beta", null, 40, "", true),
            new InvestmentChoiceFund("F06", "Zero", null, 10, "0", true),
            new InvestmentChoiceFund("F07", "Closed Fund", null, 70, "2", false),
            new InvestmentChoiceFund("F08", "Unavailable", null, 80, "1", false)),
        List.of(
            new InvestmentChoiceMandate(LocalDate.of(2026, 10, 1), "BO", "F01", "99"),
            new InvestmentChoiceMandate(LocalDate.of(2026, 10, 1), "EE", "F06", "90"),
            new InvestmentChoiceMandate(LocalDate.of(2026, 10, 1), "EE", "F07", "04"),
            new InvestmentChoiceMandate(LocalDate.of(2026, 10, 1), "EE", "F02", "05"),
            new InvestmentChoiceMandate(LocalDate.of(2026, 10, 1), "EE", "UNKNOWN", "03"),
            new InvestmentChoiceMandate(LocalDate.of(2025, 1, 2), "EE", "MISSING", "12")),
        List.of(
            new InvestmentChoicePending(
                "REF-OLD",
                LocalDate.of(2026, 8, 3),
                LocalTime.of(9, 0),
                List.of(new InvestmentChoicePendingDetail("EE", "MISSING", 100))),
            new InvestmentChoicePending(
                "REF-NEW",
                LocalDate.of(2026, 10, 2),
                LocalTime.of(14, 30, 5),
                List.of(
                    new InvestmentChoicePendingDetail("BO", "F01", 6),
                    new InvestmentChoicePendingDetail("EE", "F07", 7),
                    new InvestmentChoicePendingDetail("EE", "F02", 8)))));
  }
}
