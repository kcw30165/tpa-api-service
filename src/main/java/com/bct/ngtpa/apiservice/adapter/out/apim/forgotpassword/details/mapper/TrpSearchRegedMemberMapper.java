package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.mapper;

import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.dto.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TrpSearchRegedMemberMapper {
  private static final Logger LOGGER = LoggerFactory.getLogger(TrpSearchRegedMemberMapper.class);

  public TrpSearchRegedMemberRequest toRequest(
      ForgotPasswordDetailsCommand command,
      String publicType,
      String identityValue,
      String customerId) {
    String searchBy =
        "STAFF_NO".equalsIgnoreCase(publicType)
            ? "STAFFNO"
            : publicType.trim().toUpperCase(Locale.ROOT);
    return new TrpSearchRegedMemberRequest(
        searchBy,
        identityValue,
        command.dateOfBirth().trim(),
        hasText(command.email()) ? command.email().trim() : "",
        hasText(command.mobile()) ? normalizeMobile(command.mobile()) : "",
        customerId);
  }

  public ForgotPasswordDetailsLookupResult toResult(
      String err, List<TrpSearchRegedMemberDataItem> data, ForgotPasswordDetailsCommand command) {
    String safe = text(err);
    List<TrpSearchRegedMemberDataItem> items = data == null ? List.of() : List.copyOf(data);
    if ("err.tpa.member.not.found".equals(safe))
      return new ForgotPasswordDetailsLookupResult(ForgotPasswordDetailsLookupStatus.NOT_FOUND);
    if (safe != null && !safe.isBlank()) {
      LOGGER.error(
          "event=forgot-password.details.invalid-response reason=unexpected-business-error errorCode={}",
          safe);
      throw new ForgotPasswordDetailsDownstreamException(
          ForgotPasswordDetailsDownstreamException.Reason.UNEXPECTED_ERROR);
    }
    if (items.isEmpty())
      throw new ForgotPasswordDetailsDownstreamException(
          ForgotPasswordDetailsDownstreamException.Reason.EMPTY_DATA);
    if (items.size() != 1)
      throw new ForgotPasswordDetailsDownstreamException(
          ForgotPasswordDetailsDownstreamException.Reason.AMBIGUOUS_DATA);
    var item = items.get(0);
    if (item == null)
      throw new ForgotPasswordDetailsDownstreamException(
          ForgotPasswordDetailsDownstreamException.Reason.MALFORMED_DATA);
    boolean emailRequest = hasText(command.email());
    boolean matched =
        emailRequest
            ? command.email().trim().equalsIgnoreCase(textOrEmpty(item.email()))
                && !hasText(item.mobileNumber())
            : normalizeMobile(command.mobile()).equals(normalizeMobile(item.mobileNumber()))
                && !hasText(item.email());
    if (!matched)
      throw new ForgotPasswordDetailsDownstreamException(
          ForgotPasswordDetailsDownstreamException.Reason.CONTACT_MISMATCH);
    String env = textOrEmpty(item.env());
    return new ForgotPasswordDetailsLookupResult(
        ForgotPasswordDetailsLookupStatus.MATCHED, env.isBlank() ? null : env);
  }

  private static boolean hasText(String v) {
    return v != null && !v.isBlank();
  }

  private static String text(String v) {
    return hasText(v) ? v.trim() : null;
  }

  private static String textOrEmpty(String v) {
    return text(v) == null ? "" : text(v);
  }

  private static String normalizeMobile(String v) {
    String d = v == null ? "" : v.replaceAll("\\D", "");
    return d.matches("852\\d{8}") ? d.substring(3) : d;
  }
}
