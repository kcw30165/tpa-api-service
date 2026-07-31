package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto;
public record ForgotPasswordIdentityCheckRequest(String identityType,String identityValue){@Override public String toString(){return "ForgotPasswordIdentityCheckRequest[identityType="+identityType+",identityValue=<redacted>]";}}
