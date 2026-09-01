package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.mapper;

import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.dto.ForgotPasswordTrpSearchMemberDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.dto.ForgotPasswordTrpSearchMemberRequest;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsDownstreamException;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsLookupCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsLookupResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsLookupStatus;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Maps Forgot Password details verification to the TRPSearchMember wire contract. */
public final class ForgotPasswordTrpSearchMemberMapper {
  private static final String MEMBER_NOT_FOUND = "err.tpa.member.not.found";
  private static final DateTimeFormatter TERM_COMPLETE_DATE =
      DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

  public ForgotPasswordTrpSearchMemberRequest toRequest(ForgotPasswordDetailsLookupCommand command) {
    String searchBy =
        "STAFF_NO".equalsIgnoreCase(command.searchBy())
            ? "STAFFNO"
            : command.searchBy().trim().toUpperCase(Locale.ROOT);
    return new ForgotPasswordTrpSearchMemberRequest(
        searchBy,
        command.searchValue(),
        command.dateOfBirth().trim(),
        hasText(command.email()) ? command.email().trim() : "",
        hasText(command.mobile()) ? normalizeMobile(command.mobile()) : "");
  }

  public ForgotPasswordDetailsLookupResult toResult(
      String error,
      List<ForgotPasswordTrpSearchMemberDataItem> data,
      String expectedCustomerId) {
    String safeError = text(error);
    if (MEMBER_NOT_FOUND.equals(safeError)) {
      return notFound();
    }
    if (safeError != null) {
      throw malformed(ForgotPasswordDetailsDownstreamException.Reason.UNEXPECTED_ERROR);
    }
    List<ForgotPasswordTrpSearchMemberDataItem> items = data == null ? List.of() : data;
    if (items.isEmpty()) {
      return notFound();
    }
    String expected = requiredText(expectedCustomerId);
    Set<String> seenEnvironmentTrust = new HashSet<>();
    ForgotPasswordTrpSearchMemberDataItem matched = null;
    for (ForgotPasswordTrpSearchMemberDataItem item : items) {
      if (item == null) {
        throw malformed(ForgotPasswordDetailsDownstreamException.Reason.MALFORMED_DATA);
      }
      String env = text(item.env());
      String trustCode = text(item.trustCode());
      Boolean registered = item.isRegistered();
      if (env == null || trustCode == null || registered == null) {
        throw malformed(ForgotPasswordDetailsDownstreamException.Reason.MALFORMED_DATA);
      }
      if (!seenEnvironmentTrust.add(env + "|" + trustCode)) {
        throw malformed(ForgotPasswordDetailsDownstreamException.Reason.AMBIGUOUS_DATA);
      }
      if (!validTermCompleteDate(text(item.termCompleteDate()))) {
        throw malformed(ForgotPasswordDetailsDownstreamException.Reason.MALFORMED_DATA);
      }
      String customerId = text(item.customerId());
      if (registered && customerId == null) {
        throw malformed(ForgotPasswordDetailsDownstreamException.Reason.MALFORMED_DATA);
      }
      if (!registered && customerId != null) {
        throw malformed(ForgotPasswordDetailsDownstreamException.Reason.MALFORMED_DATA);
      }
      if (registered && expected.equals(customerId) && matched == null) {
        matched = item;
      }
    }
    return matched == null
        ? notFound()
        : new ForgotPasswordDetailsLookupResult(
            ForgotPasswordDetailsLookupStatus.MATCHED, text(matched.env()));
  }

  private static boolean validTermCompleteDate(String value) {
    if (value == null) return true;
    try {
      LocalDate.parse(value, TERM_COMPLETE_DATE);
      return true;
    } catch (DateTimeParseException exception) {
      return false;
    }
  }

  private static ForgotPasswordDetailsLookupResult notFound() {
    return new ForgotPasswordDetailsLookupResult(ForgotPasswordDetailsLookupStatus.NOT_FOUND);
  }

  private static ForgotPasswordDetailsDownstreamException malformed(
      ForgotPasswordDetailsDownstreamException.Reason reason) {
    return new ForgotPasswordDetailsDownstreamException(reason);
  }

  private static String requiredText(String value) {
    String result = text(value);
    if (result == null) throw new IllegalArgumentException("expectedCustomerId must not be blank");
    return result;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static String text(String value) {
    return hasText(value) ? value.trim() : null;
  }

  private static String normalizeMobile(String value) {
    String digits = value == null ? "" : value.replaceAll("\\D", "");
    return digits.matches("852\\d{8}") ? digits.substring(3) : digits;
  }
}
