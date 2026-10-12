package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpSearchMemberApimDataItem;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApimRegistrationLookupAdapterEnvelopeTest {

  @Test
  void envelopeFactoryKeepsBusinessErrMessageInspectable() {
    var envelope = new ApimRegistrationLookupEnvelope("err.tpa.member.not.found", List.of());

    assertThat(envelope.errMessage()).isEqualTo("err.tpa.member.not.found");
    assertThat(envelope.data()).isEmpty();
  }

  @Test
  void envelopeDefensivelyCopiesDataAndRedactsToString() {
    var data = new java.util.ArrayList<TrpSearchMemberApimDataItem>();
    data.add(new TrpSearchMemberApimDataItem("OI", "JPM", null, null, null, null, null, Boolean.FALSE, null));

    var envelope = new ApimRegistrationLookupEnvelope("", data);
    data.clear();

    assertThat(envelope.data()).hasSize(1);
    assertThat(envelope.toString()).doesNotContain("member@example.com", "91234567");
  }

  @Test
  void adapterDoesNotHardcodeUserFacingMessages() throws Exception {
    var source =
        java.nio.file.Files.readString(
            java.nio.file.Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/registration/ApimRegistrationLookupAdapter.java"));

    assertThat(source).doesNotContain("We are unable to verify your details", "我們未能核實您的資料");
  }
}
