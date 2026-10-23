package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.validation.PersonalInformationFieldAccessChecker;
import java.util.List;
import reactor.core.publisher.Mono;

/** Controller tests that are not about field access use a checker that finds no access errors. */
final class PersonalInformationAccessCheckerTestSupport {

  private PersonalInformationAccessCheckerTestSupport() {}

  static PersonalInformationFieldAccessChecker passing() {
    PersonalInformationFieldAccessChecker checker =
        mock(PersonalInformationFieldAccessChecker.class);
    when(checker.check(any(), any(), any(), any(), any())).thenReturn(Mono.just(List.of()));
    return checker;
  }
}
