package com.bct.ngtpa.apiservice.application.usecase.auth.login.account;

import com.bct.ngtpa.apiservice.application.dto.auth.login.account.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;

public final class LoginAccountRecordValidationService {
    private static final DateTimeFormatter TERM_DATE =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private final int policyWidth;
    private final int certWidth;
    private final char padCharacter;

    public LoginAccountRecordValidationService(int policyWidth,int certWidth,char padCharacter){
        if(policyWidth<=0||certWidth<=0)throw new IllegalArgumentException("identifier widths must be positive");
        if(!Character.isDigit(padCharacter))throw new IllegalArgumentException("padCharacter must be numeric");
        this.policyWidth=policyWidth;this.certWidth=certWidth;this.padCharacter=padCharacter;
    }

    public LoginAccountValidationResult validate(List<LoginAccountRecord> source){
        List<LoginAccountRecord> records=source==null?List.of():List.copyOf(source);
        List<LoginValidatedAccount> valid=new ArrayList<>();
        List<LoginAccountRecordRejection> rejected=new ArrayList<>();
        for(int index=0;index<records.size();index++){
            LoginAccountRecord record=records.get(index);
            try{valid.add(validateRecord(record));}
            catch(RecordInvalid exception){rejected.add(new LoginAccountRecordRejection(index,exception.category));}
        }
        LoginAccountValidationStatus status=valid.isEmpty()?LoginAccountValidationStatus.ZERO_VALID_RECORDS:LoginAccountValidationStatus.VALID;
        return new LoginAccountValidationResult(status,valid,rejected,records.size());
    }

    private LoginValidatedAccount validateRecord(LoginAccountRecord record){
        if(record==null)throw invalid(LoginAccountRecordRejectionCategory.REQUIRED_FIELD);
        String env=required(record.accountEnv());String trust=required(record.trustCode());
        String rawPolicy=trim(record.rawPolicyNo());
        if(rawPolicy==null||!rawPolicy.matches("[0-9]+")||rawPolicy.length()>policyWidth||allZero(rawPolicy))throw invalid(LoginAccountRecordRejectionCategory.POLICY_NUMBER);
        if(record.rawCertNo()<=0)throw invalid(LoginAccountRecordRejectionCategory.CERTIFICATE_NUMBER);
        String rawCert=Integer.toString(record.rawCertNo());
        if(rawCert.length()>certWidth)throw invalid(LoginAccountRecordRejectionCategory.CERTIFICATE_NUMBER);
        String status=trim(record.termStatus());String date=trim(record.termCompletionDate());LocalDate parsed=null;
        if(status==null&&date==null){}
        else if("T".equals(status)&&date!=null){try{parsed=LocalDate.parse(date,TERM_DATE);}catch(RuntimeException exception){throw invalid(LoginAccountRecordRejectionCategory.TERMINATION_PAIR);}}
        else throw invalid(LoginAccountRecordRejectionCategory.TERMINATION_PAIR);
        return new LoginValidatedAccount(env,trust,rawPolicy,leftPad(rawPolicy,policyWidth),trim(record.schemeType()),record.rawCertNo(),leftPad(rawCert,certWidth),status,parsed);
    }

    private String required(String value){String result=trim(value);if(result==null)throw invalid(LoginAccountRecordRejectionCategory.REQUIRED_FIELD);return result;}
    private String trim(String value){return value==null||value.isBlank()?null:value.trim();}
    private boolean allZero(String value){return value.chars().allMatch(ch->ch=='0');}
    private String leftPad(String value,int width){return String.valueOf(padCharacter).repeat(width-value.length())+value;}
    private RecordInvalid invalid(LoginAccountRecordRejectionCategory category){return new RecordInvalid(category);}
    private static final class RecordInvalid extends RuntimeException{private final LoginAccountRecordRejectionCategory category;private RecordInvalid(LoginAccountRecordRejectionCategory category){super(null,null,false,false);this.category=category;}}
}
