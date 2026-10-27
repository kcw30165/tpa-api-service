package com.bct.ngtpa.apiservice.application.usecase.auth.login.account;

import com.bct.ngtpa.apiservice.application.dto.auth.login.account.*;
import com.bct.ngtpa.apiservice.application.service.account.AccountIdentifierFormatter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class LoginAccountRecordValidationService {
  private static final DateTimeFormatter TERM_DATE =
      DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
  private final AccountIdentifierFormatter accountIdentifierFormatter;

  public LoginAccountRecordValidationService(int policyWidth, int certWidth, char padCharacter) {
    this(new AccountIdentifierFormatter(policyWidth, certWidth, padCharacter));
  }

  public LoginAccountRecordValidationService(
      AccountIdentifierFormatter accountIdentifierFormatter) {
    this.accountIdentifierFormatter =
        Objects.requireNonNull(accountIdentifierFormatter, "accountIdentifierFormatter");
  }

  public LoginAccountValidationResult validate(List<LoginAccountRecord> source) {
    List<LoginAccountRecord> records = source == null ? List.of() : List.copyOf(source);
    List<LoginValidatedAccount> valid = new ArrayList<>();
    List<LoginAccountRecordRejection> rejected = new ArrayList<>();
    for (int index = 0; index < records.size(); index++) {
      LoginAccountRecord record = records.get(index);
      try {
        valid.add(validateRecord(record));
      } catch (RecordInvalid exception) {
        rejected.add(new LoginAccountRecordRejection(index, exception.category));
      }
    }
    LoginAccountValidationStatus status =
        valid.isEmpty()
            ? LoginAccountValidationStatus.ZERO_VALID_RECORDS
            : LoginAccountValidationStatus.VALID;
    return new LoginAccountValidationResult(status, valid, rejected, records.size());
  }

  private LoginValidatedAccount validateRecord(LoginAccountRecord record) {
    if (record == null) throw invalid(LoginAccountRecordRejectionCategory.REQUIRED_FIELD);
    String env = required(record.accountEnv());
    String trust = required(record.trustCode());
    String rawPolicy = trim(record.rawPolicyNo());
    if (rawPolicy == null
        || !rawPolicy.matches("[0-9]+")
        || rawPolicy.length() > accountIdentifierFormatter.policyWidth()
        || allZero(rawPolicy)) throw invalid(LoginAccountRecordRejectionCategory.POLICY_NUMBER);
    if (record.rawCertNo() <= 0)
      throw invalid(LoginAccountRecordRejectionCategory.CERTIFICATE_NUMBER);
    String rawCert = Integer.toString(record.rawCertNo());
    if (rawCert.length() > accountIdentifierFormatter.certificateWidth())
      throw invalid(LoginAccountRecordRejectionCategory.CERTIFICATE_NUMBER);
    String status = trim(record.termStatus());
    String date = trim(record.termCompletionDate());
    LocalDate parsed = null;
    if (status == null && date == null) {
    } else if ("T".equals(status) && date != null) {
      try {
        parsed = LocalDate.parse(date, TERM_DATE);
      } catch (RuntimeException exception) {
        throw invalid(LoginAccountRecordRejectionCategory.TERMINATION_PAIR);
      }
    } else throw invalid(LoginAccountRecordRejectionCategory.TERMINATION_PAIR);
    return new LoginValidatedAccount(
        env,
        trust,
        rawPolicy,
        accountIdentifierFormatter.formatPolicyNo(rawPolicy),
        trim(record.schemeType()),
        record.rawCertNo(),
        accountIdentifierFormatter.formatCertificateNo(rawCert),
        status,
        parsed);
  }

  private String required(String value) {
    String result = trim(value);
    if (result == null) throw invalid(LoginAccountRecordRejectionCategory.REQUIRED_FIELD);
    return result;
  }

  private String trim(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private boolean allZero(String value) {
    return value.chars().allMatch(ch -> ch == '0');
  }

  private RecordInvalid invalid(LoginAccountRecordRejectionCategory category) {
    return new RecordInvalid(category);
  }

  private static final class RecordInvalid extends RuntimeException {
    private final LoginAccountRecordRejectionCategory category;

    private RecordInvalid(LoginAccountRecordRejectionCategory category) {
      super(null, null, false, false);
      this.category = category;
    }
  }
}
