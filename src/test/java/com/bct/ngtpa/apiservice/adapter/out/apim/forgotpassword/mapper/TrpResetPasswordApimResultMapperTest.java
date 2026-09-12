package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.TrpResetPasswordApimEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.dto.TrpResetPasswordApimDataItem;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.ForgotPasswordCompletionResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrpResetPasswordApimResultMapperTest {
  private final TrpResetPasswordApimResultMapper mapper = new TrpResetPasswordApimResultMapper();

  private static TrpResetPasswordApimEnvelope success(String custId) {
    return new TrpResetPasswordApimEnvelope(
        "", List.of(new TrpResetPasswordApimDataItem(true, custId, "01/01/2026", "10:00:00")));
  }

  @Test
  void resetsWhenCustomerIdMatchesExactly() {
    var result = mapper.toResult(success("JPJPM02"), "JPJPM02");
    assertThat(result.status()).isEqualTo(ForgotPasswordCompletionResult.Status.RESET);
    assertThat(result.updateDate()).isEqualTo("01/01/2026");
    assertThat(result.updateTime()).isEqualTo("10:00:00");
  }

  @Test
  void resetsWhenCustomerIdDiffersOnlyByCaseOrWhitespace() {
    assertThat(mapper.toResult(success(" jpjpm02 "), "JPJPM02").status())
        .isEqualTo(ForgotPasswordCompletionResult.Status.RESET);
    assertThat(mapper.toResult(success("JPJPM02"), "  jpjpm02").status())
        .isEqualTo(ForgotPasswordCompletionResult.Status.RESET);
  }

  @Test
  void technicalFailureWhenCustomerIdIsGenuinelyDifferent() {
    assertThat(mapper.toResult(success("OTHER99"), "JPJPM02").status())
        .isEqualTo(ForgotPasswordCompletionResult.Status.TECHNICAL_FAILURE);
  }

  @Test
  void rejectsRecognizedBusinessErrorCodes() {
    for (String code :
        List.of(
            "err.tpa.password.invalid",
            "err.tpa.password.used.in.last.5.changes.",
            "err.tpa.member.not.found")) {
      var result = mapper.toResult(new TrpResetPasswordApimEnvelope(code, List.of()), "JPJPM02");
      assertThat(result.status()).isEqualTo(ForgotPasswordCompletionResult.Status.REJECTED);
      assertThat(result.downstreamErrorCode()).isEqualTo(code);
    }
  }

  @Test
  void technicalFailureForUnrecognizedErrorNullEnvelopeAndBadTimestamp() {
    assertThat(
            mapper
                .toResult(new TrpResetPasswordApimEnvelope("err.tpa.some.other", List.of()), "JPJPM02")
                .status())
        .isEqualTo(ForgotPasswordCompletionResult.Status.TECHNICAL_FAILURE);
    assertThat(mapper.toResult(null, "JPJPM02").status())
        .isEqualTo(ForgotPasswordCompletionResult.Status.TECHNICAL_FAILURE);
    var badTime =
        new TrpResetPasswordApimEnvelope(
            "", List.of(new TrpResetPasswordApimDataItem(true, "JPJPM02", "01/01/2026", "99:99:99")));
    assertThat(mapper.toResult(badTime, "JPJPM02").status())
        .isEqualTo(ForgotPasswordCompletionResult.Status.TECHNICAL_FAILURE);
  }
}
