package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.identity;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationIdentityType;
public final class ForgotPasswordIdentityVerifier {
 public RegistrationIdentityType mapPublicIdentityType(String value){if(value==null)return null;return switch(value){case "GPN"->RegistrationIdentityType.GPN;case "GPFS"->RegistrationIdentityType.GPFS;case "STAFF_NO"->RegistrationIdentityType.STAFFNO;case "ID"->RegistrationIdentityType.ID;default->null;};}
 public boolean matches(String type,String value,String protectedType,String protectedValue){RegistrationIdentityType mapped=mapPublicIdentityType(type);return mapped!=null&&value!=null&&protectedType!=null&&protectedValue!=null&&mapped.name().equals(protectedType)&&value.trim().equals(protectedValue);}
}
