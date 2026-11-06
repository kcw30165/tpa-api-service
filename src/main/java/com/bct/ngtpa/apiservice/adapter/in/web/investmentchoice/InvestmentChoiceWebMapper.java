package com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice;

import com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.response.InvestmentChoiceAllocationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.response.InvestmentChoiceDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.response.InvestmentChoiceFundResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.response.InvestmentChoiceHistoryResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.response.InvestmentChoicePendingAllocationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.response.InvestmentChoicePendingInstructionResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.response.InvestmentChoiceSwitchSourceResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.support.RequestLanguageResolver;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceRiskLevels;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceDetails;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceFund;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceMandate;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoicePending;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoicePendingDetail;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceResult;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public final class InvestmentChoiceWebMapper {

  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT)
          .withResolverStyle(ResolverStyle.STRICT);
  private static final DateTimeFormatter TIME_FORMATTER =
      DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

  public InvestmentChoiceDataResponse toDataResponse(
      InvestmentChoiceResult result, String language) {
    Objects.requireNonNull(result, "result must not be null");
    InvestmentChoiceDetails details = result.details();
    boolean chinese = RequestLanguageResolver.isZhHk(language);

    List<InvestmentChoiceSource> sources =
        details.sources().stream()
            .sorted(
                Comparator.comparingInt(InvestmentChoiceSource::seq)
                    .thenComparing(
                        InvestmentChoiceSource::code,
                        Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    List<InvestmentChoiceFund> allFunds = orderedFunds(details.funds(), chinese);
    Map<String, String> fundNames = fundNames(allFunds, chinese);
    Map<String, Integer> sourceSequence = sourceSequence(sources);
    Map<String, Integer> fundOrder = fundOrder(allFunds);
    Comparator<InvestmentChoiceMandate> mandateOrder =
        Comparator.comparingInt(
                (InvestmentChoiceMandate mandate) ->
                    sourceSequence.getOrDefault(mandate.switchSource(), Integer.MAX_VALUE))
            .thenComparing(
                InvestmentChoiceMandate::switchSource,
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingInt(
                mandate -> fundOrder.getOrDefault(mandate.fundCode(), Integer.MAX_VALUE))
            .thenComparing(
                InvestmentChoiceMandate::fundCode, Comparator.nullsLast(Comparator.naturalOrder()));
    Comparator<InvestmentChoicePendingDetail> pendingDetailOrder =
        Comparator.comparingInt(
                (InvestmentChoicePendingDetail detail) ->
                    sourceSequence.getOrDefault(detail.switchSource(), Integer.MAX_VALUE))
            .thenComparing(
                InvestmentChoicePendingDetail::switchSource,
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingInt(
                detail -> fundOrder.getOrDefault(detail.fundCode(), Integer.MAX_VALUE))
            .thenComparing(
                InvestmentChoicePendingDetail::fundCode,
                Comparator.nullsLast(Comparator.naturalOrder()));

    return new InvestmentChoiceDataResponse(
        result.accountNumber(),
        details.intervalPcnt(),
        sources.stream()
            .map(
                source ->
                    new InvestmentChoiceSwitchSourceResponse(
                        source.code(), source.seq(), source.avail()))
            .toList(),
        allFunds.stream()
            .filter(InvestmentChoiceFund::avail)
            .map(
                fund ->
                    new InvestmentChoiceFundResponse(
                        fund.code(), displayName(fund, chinese), fund.riskLevel()))
            .toList(),
        history(details, fundNames, mandateOrder),
        pending(details, fundNames, pendingDetailOrder));
  }

  private static List<InvestmentChoiceHistoryResponse> history(
      InvestmentChoiceDetails details,
      Map<String, String> fundNames,
      Comparator<InvestmentChoiceMandate> allocationOrder) {
    Map<LocalDate, List<InvestmentChoiceMandate>> byDate = new TreeMap<>(Comparator.reverseOrder());
    details
        .mandates()
        .forEach(
            mandate ->
                byDate
                    .computeIfAbsent(mandate.effectiveDate(), ignored -> new ArrayList<>())
                    .add(mandate));
    return byDate.entrySet().stream()
        .map(
            entry ->
                new InvestmentChoiceHistoryResponse(
                    DATE_FORMATTER.format(entry.getKey()),
                    entry.getValue().stream()
                        .sorted(allocationOrder)
                        .map(
                            mandate ->
                                new InvestmentChoiceAllocationResponse(
                                    mandate.switchSource(),
                                    mandate.fundCode(),
                                    fundNames.get(mandate.fundCode()),
                                    mandate.percentage()))
                        .toList()))
        .toList();
  }

  private static List<InvestmentChoicePendingInstructionResponse> pending(
      InvestmentChoiceDetails details,
      Map<String, String> fundNames,
      Comparator<InvestmentChoicePendingDetail> allocationOrder) {
    return details.pending().stream()
        .sorted(
            Comparator.comparing(InvestmentChoicePending::submitDate)
                .thenComparing(InvestmentChoicePending::submitTime)
                .reversed())
        .map(
            instruction ->
                new InvestmentChoicePendingInstructionResponse(
                    instruction.refNo(),
                    DATE_FORMATTER.format(instruction.submitDate()),
                    TIME_FORMATTER.format(instruction.submitTime()),
                    instruction.details().stream()
                        .sorted(allocationOrder)
                        .map(
                            detail ->
                                new InvestmentChoicePendingAllocationResponse(
                                    detail.switchSource(),
                                    detail.fundCode(),
                                    fundNames.get(detail.fundCode()),
                                    detail.percentage()))
                        .toList()))
        .toList();
  }

  private static List<InvestmentChoiceFund> orderedFunds(
      List<InvestmentChoiceFund> funds, boolean chinese) {
    Comparator<InvestmentChoiceFund> order =
        Comparator.comparing(
                (InvestmentChoiceFund fund) -> FundPriceRiskLevels.sortKey(fund.riskLevel()),
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(fund -> caseless(displayName(fund, chinese)))
            .thenComparing(
                InvestmentChoiceFund::code, Comparator.nullsLast(Comparator.naturalOrder()));
    return funds.stream().sorted(order).toList();
  }

  private static Map<String, String> fundNames(List<InvestmentChoiceFund> funds, boolean chinese) {
    Map<String, String> names = new HashMap<>();
    funds.forEach(fund -> names.putIfAbsent(fund.code(), displayName(fund, chinese)));
    return names;
  }

  private static Map<String, Integer> sourceSequence(List<InvestmentChoiceSource> sources) {
    Map<String, Integer> sequence = new HashMap<>();
    sources.forEach(source -> sequence.putIfAbsent(source.code(), source.seq()));
    return sequence;
  }

  private static Map<String, Integer> fundOrder(List<InvestmentChoiceFund> funds) {
    Map<String, Integer> order = new HashMap<>();
    for (int index = 0; index < funds.size(); index++) {
      order.putIfAbsent(funds.get(index).code(), index);
    }
    return order;
  }

  private static String displayName(InvestmentChoiceFund fund, boolean chinese) {
    return chinese && StringUtils.hasText(fund.chineseName()) ? fund.chineseName() : fund.name();
  }

  private static String caseless(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }
}
