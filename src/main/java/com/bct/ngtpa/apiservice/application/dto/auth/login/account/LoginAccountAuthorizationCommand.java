package com.bct.ngtpa.apiservice.application.dto.auth.login.account;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.util.Objects;
public record LoginAccountAuthorizationCommand(String custId, String searchKey, String searchValue, String correlationId, ActorUserRole actorUserRole) {
    public LoginAccountAuthorizationCommand { custId=required(custId,"custId");searchKey=required(searchKey,"searchKey");searchValue=required(searchValue,"searchValue");correlationId=correlationId==null||correlationId.isBlank()?null:correlationId.trim();Objects.requireNonNull(actorUserRole); }
    private static String required(String v,String n){if(v==null||v.isBlank())throw new IllegalArgumentException(n+" must not be blank");return v.trim();}
    @Override public String toString(){return "LoginAccountAuthorizationCommand[custId=<redacted>, searchKey="+searchKey+", searchValue=<redacted>, correlationId="+correlationId+", actorUserRole="+actorUserRole+"]";}
}
