package com.bct.ngtpa.apiservice.integration.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.support.RequestLanguageResolver;
import com.bct.ngtpa.apiservice.integration.registration.support.*;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import reactor.util.context.Context;

@ExtendWith(RegistrationTestResultExtension.class)
class RegistrationLanguageCorrelationIntegrationTest {
  @Test
  @RegistrationTestCase("TC-074")
  void acceptLanguageIsCanonicalAndObsoleteFallbackIsIgnored() {
    assertThat(
            RequestLanguageResolver.resolve(
                new RequestHeaderContext("ACC-ignored", "REQ-1", "zh-HK"), "en-US,en;q=0.9", "en"))
        .isEqualTo("zh_HK");
    assertThat(RequestLanguageResolver.resolve(null, "en-US,en;q=0.9", "zh_HK")).isEqualTo("en");
    assertThat(RequestLanguageResolver.resolve(null, "zh", "en")).isEqualTo("zh_HK");
    assertThat(RequestLanguageResolver.resolve(null, null, "zh_HK")).isEqualTo("en");
  }

  @Test
  @RegistrationTestCase("TC-075")
  void requestCorrelationUsesOnlyRequestIdContextKey() {
    Context context = Context.of(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, "req-known-075");
    String requestId = context.get(RequestCorrelation.REQUEST_ID_CONTEXT_KEY);
    assertThat(requestId).isEqualTo("req-known-075");
    assertThat(context.stream().map(e -> e.getKey().toString()))
        .containsExactly(RequestCorrelation.REQUEST_ID_CONTEXT_KEY);
    assertThat(context.toString()).doesNotContain("Account-Ref", "Accept-Language");
  }
}
