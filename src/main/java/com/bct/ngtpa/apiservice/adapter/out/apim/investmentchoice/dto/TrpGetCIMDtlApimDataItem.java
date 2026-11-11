package com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TrpGetCIMDtlApimDataItem(
    @JsonProperty("interval-pcnt") Integer intervalPcnt,
    @JsonProperty("SwitchSrc") List<SwitchSrc> switchSrc,
    @JsonProperty("SwitchFund") List<SwitchFund> switchFund,
    @JsonProperty("InvestMandate") List<InvestMandate> investMandate,
    @JsonProperty("PendingReq") List<PendingReq> pendingReq,
    @JsonProperty("PendingReqDtl") List<PendingReqDtl> pendingReqDtl) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SwitchSrc(
      @JsonProperty("switch-src") String switchSrc,
      @JsonProperty("src-desc") String srcDesc,
      @JsonProperty("src-chin-desc") String srcChinDesc,
      @JsonProperty("seq") Integer seq,
      @JsonProperty("avail-sw") Boolean availSw) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SwitchFund(
      @JsonProperty("fund-code") String fundCode,
      @JsonProperty("fund-name") String fundName,
      @JsonProperty("chin-fund-name") String chinFundName,
      @JsonProperty("fund-seq") Integer fundSeq,
      @JsonProperty("risk-level") String riskLevel,
      @JsonProperty("avail-sw") Boolean availSw) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record InvestMandate(
      @JsonProperty("effect-date") String effectDate,
      @JsonProperty("switch-src") String switchSrc,
      @JsonProperty("fund-code") String fundCode,
      @JsonProperty("pcnt") String pcnt) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PendingReq(
      @JsonProperty("ref-no") String refNo,
      @JsonProperty("submit-date") String submitDate,
      @JsonProperty("submit-time") String submitTime) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PendingReqDtl(
      @JsonProperty("ref-no") String refNo,
      @JsonProperty("switch-src") String switchSrc,
      @JsonProperty("fund-code") String fundCode,
      @JsonProperty("pcnt") Integer pcnt) {}
}
