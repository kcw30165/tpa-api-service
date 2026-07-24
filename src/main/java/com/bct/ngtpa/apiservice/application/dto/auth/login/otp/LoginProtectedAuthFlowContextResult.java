package com.bct.ngtpa.apiservice.application.dto.auth.login.otp;
import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.LoginPreAuthContext;
public record LoginProtectedAuthFlowContextResult(LoginProtectedAuthFlowContextStatus status,LoginPreAuthContext context){
 public static LoginProtectedAuthFlowContextResult valid(LoginPreAuthContext context){return new LoginProtectedAuthFlowContextResult(LoginProtectedAuthFlowContextStatus.VALID,context);}
 public static LoginProtectedAuthFlowContextResult invalid(LoginProtectedAuthFlowContextStatus status){return new LoginProtectedAuthFlowContextResult(status,null);}
 public boolean isValid(){return status==LoginProtectedAuthFlowContextStatus.VALID&&context!=null;}
 @Override public String toString(){return "LoginProtectedAuthFlowContextResult[status="+status+", context=<redacted>]";}
}
