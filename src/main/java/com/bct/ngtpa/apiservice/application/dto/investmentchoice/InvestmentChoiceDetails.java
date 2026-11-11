package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

import java.util.List;
import java.util.Objects;

public sealed interface InvestmentChoiceDetails
    permits InvestmentChoiceDetails.Normal, InvestmentChoiceDetails.Restricted {

  InvestmentChoiceScenario scenario();

  List<InvestmentChoiceSource> historySources();

  List<InvestmentChoiceFund> historyFunds();

  List<InvestmentChoiceMandate> mandates();

  record Normal(
      int intervalPcnt,
      List<InvestmentChoiceSource> sources,
      List<InvestmentChoiceFund> funds,
      List<InvestmentChoiceMandate> mandates,
      List<InvestmentChoicePending> pending)
      implements InvestmentChoiceDetails {

    public Normal {
      sources = List.copyOf(Objects.requireNonNull(sources, "sources must not be null"));
      funds = List.copyOf(Objects.requireNonNull(funds, "funds must not be null"));
      mandates = List.copyOf(Objects.requireNonNull(mandates, "mandates must not be null"));
      pending = List.copyOf(Objects.requireNonNull(pending, "pending must not be null"));
    }

    @Override
    public InvestmentChoiceScenario scenario() {
      return InvestmentChoiceScenario.NORMAL;
    }

    @Override
    public List<InvestmentChoiceSource> historySources() {
      return sources;
    }

    @Override
    public List<InvestmentChoiceFund> historyFunds() {
      return funds;
    }
  }

  record Restricted(
      InvestmentChoiceScenario scenario,
      List<InvestmentChoiceSource> sourceMetadata,
      List<InvestmentChoiceFund> funds,
      List<InvestmentChoiceMandate> mandates)
      implements InvestmentChoiceDetails {

    public Restricted {
      Objects.requireNonNull(scenario, "scenario must not be null");
      if (scenario == InvestmentChoiceScenario.NORMAL) {
        throw new IllegalArgumentException("Restricted details require a restricted scenario");
      }
      sourceMetadata =
          List.copyOf(Objects.requireNonNull(sourceMetadata, "sourceMetadata must not be null"));
      funds = List.copyOf(Objects.requireNonNull(funds, "funds must not be null"));
      mandates = List.copyOf(Objects.requireNonNull(mandates, "mandates must not be null"));
    }

    @Override
    public List<InvestmentChoiceSource> historySources() {
      return sourceMetadata;
    }

    @Override
    public List<InvestmentChoiceFund> historyFunds() {
      return funds;
    }
  }
}
