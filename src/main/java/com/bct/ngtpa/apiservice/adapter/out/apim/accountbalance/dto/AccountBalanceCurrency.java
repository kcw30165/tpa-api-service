package com.bct.ngtpa.apiservice.adapter.out.apim.accountbalance.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
public record AccountBalanceCurrency(@JsonProperty("avai") List<String> available, @JsonProperty("selected") String selected) {}
