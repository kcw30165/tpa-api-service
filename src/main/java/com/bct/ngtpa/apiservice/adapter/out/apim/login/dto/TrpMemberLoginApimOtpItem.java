package com.bct.ngtpa.apiservice.adapter.out.apim.login.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TrpMemberLoginApimOtpItem(
    @JsonProperty("channel") String channel, @JsonProperty("destination") String destination) {

  @Override
  public String toString() {
    return "TrpMemberLoginApimOtpItem[channel=" + channel + ", destination=<redacted>]";
  }
}
