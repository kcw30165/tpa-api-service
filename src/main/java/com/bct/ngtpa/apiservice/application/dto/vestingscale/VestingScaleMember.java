package com.bct.ngtpa.apiservice.application.dto.vestingscale;

import java.time.LocalDate;
import java.util.Objects;

public record VestingScaleMember(
    Presence<String> memberCategory,
    Presence<LocalDate> dateJoinedCompany,
    Presence<LocalDate> dateJoinedScheme,
    Presence<LocalDate> employmentDate) {

  public VestingScaleMember {
    Objects.requireNonNull(memberCategory, "memberCategory must not be null");
    Objects.requireNonNull(dateJoinedCompany, "dateJoinedCompany must not be null");
    Objects.requireNonNull(dateJoinedScheme, "dateJoinedScheme must not be null");
    Objects.requireNonNull(employmentDate, "employmentDate must not be null");
  }
}
