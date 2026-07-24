package com.bct.ngtpa.apiservice.adapter.out.apim.login.account.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
public record TrpGetAccountListApimRequest(
 @JsonProperty("cust-id") String custId,
 @JsonProperty("cust-type") String custType,
 @JsonProperty("search-key") String searchKey,
 @JsonProperty("search-value") String searchValue){
 @Override public String toString(){return "TrpGetAccountListApimRequest[custId=<redacted>, custType="+custType+", searchKey="+searchKey+", searchValue=<redacted>]";}
}
