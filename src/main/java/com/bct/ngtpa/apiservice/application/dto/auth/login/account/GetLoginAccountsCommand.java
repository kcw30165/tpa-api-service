package com.bct.ngtpa.apiservice.application.dto.auth.login.account;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.util.Objects;
public record GetLoginAccountsCommand(String normalizedUsername, String correlationId, ActorUserRole actorUserRole) {
    public GetLoginAccountsCommand { normalizedUsername=required(normalizedUsername,"normalizedUsername"); correlationId=correlationId==null||correlationId.isBlank()?null:correlationId.trim(); Objects.requireNonNull(actorUserRole); }
    private static String required(String v,String n){if(v==null||v.isBlank())throw new IllegalArgumentException(n+" must not be blank");return v.trim();}
    @Override public String toString(){return "GetLoginAccountsCommand[normalizedUsername=<redacted>, correlationId="+correlationId+", actorUserRole="+actorUserRole+"]";}
}
