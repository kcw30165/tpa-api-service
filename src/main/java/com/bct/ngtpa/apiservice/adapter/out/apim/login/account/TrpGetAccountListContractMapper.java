package com.bct.ngtpa.apiservice.adapter.out.apim.login.account;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.account.dto.*;import com.bct.ngtpa.apiservice.application.dto.auth.login.account.*;import java.util.Objects;
public final class TrpGetAccountListContractMapper {
 public TrpGetAccountListApimRequest toRequest(LoginAccountAuthorizationCommand c){Objects.requireNonNull(c);return new TrpGetAccountListApimRequest(c.custId(),c.custType(),c.searchKey(),c.searchValue());}
 public LoginAccountRecord toRecord(TrpGetAccountListApimDataItem i){Objects.requireNonNull(i,"TRPGetAccountList item must not be null");try{return new LoginAccountRecord(required(i.accountEnv(),"env"),required(i.trustCode(),"trust-code"),required(i.policyNo(),"policy-no"),trim(i.schemeType()),i.certNo()==null?0:i.certNo(),trim(i.termStatus()),trim(i.termCompleteDate()));}catch(IllegalArgumentException e){throw new IllegalStateException("TRPGetAccountList account item has invalid transport shape",e);}}
 private String required(String v,String n){if(v==null||v.isBlank())throw new IllegalStateException("TRPGetAccountList "+n+" is blank");return v.trim();}
 private String trim(String v){return v==null||v.isBlank()?null:v.trim();}
}
