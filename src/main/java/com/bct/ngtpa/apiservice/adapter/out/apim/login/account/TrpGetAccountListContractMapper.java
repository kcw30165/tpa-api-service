package com.bct.ngtpa.apiservice.adapter.out.apim.login.account;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.account.dto.*;import com.bct.ngtpa.apiservice.application.dto.auth.login.account.*;import java.util.Objects;
public final class TrpGetAccountListContractMapper {
 public TrpGetAccountListApimRequest toRequest(LoginAccountAuthorizationCommand c){Objects.requireNonNull(c);return new TrpGetAccountListApimRequest(c.custId(),c.custType(),c.searchKey(),c.searchValue());}
 public LoginAccountRecord toRecord(TrpGetAccountListApimDataItem i){Objects.requireNonNull(i,"TRPGetAccountList item must not be null");return new LoginAccountRecord(i.accountEnv(),i.trustCode(),i.policyNo(),i.schemeType(),i.certNo()==null?0:i.certNo(),i.termStatus(),i.termCompleteDate());}
}
