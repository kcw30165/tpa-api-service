package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateResponseMapperTest.mapper;
import static com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateResponseMapperTest.row;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersonalInformationUpdateResultArrayContractTest {
  @Test
  void selectedSuccessOtherFailureSerializesCodesOnlyAndNoInternalIdentifiers() throws Exception {
    var json = new ObjectMapper().valueToTree(mapper().toResponse(
        List.of(row(true, true, "2"), row(false, false, "3"))));
    assertThat(json).isEqualTo(new ObjectMapper().readTree("""
        {"success":true,"status":"PARTIAL_SUCCESS",
         "result":{"data":{"selectedAccount":{"accountIdentifier":"00000000118-0002",
         "refNo":null,"submitDate":"","submitTime":" "},"rows":[],"errors":[
         {"accountIdentifier":"00000000118-0003","errors":[
         {"type":"FIELD","code":"personalInformation.field.required","targets":[]}]}]}},
         "messages":[{"code":"personalInformation.update.partialSuccess"}],"errors":[]}
        """));
  }

  @Test
  void selectedFailureOtherSuccessSerializesSelectedErrorsOnlyAtRoot() throws Exception {
    var json = new ObjectMapper().valueToTree(mapper().toResponse(
        List.of(row(false, true, "3"), row(true, false, "2"))));
    assertThat(json).isEqualTo(new ObjectMapper().readTree("""
        {"success":false,"status":"VALIDATION_FAILED",
         "result":{"data":{"selectedAccount":null,"rows":[{"accountIdentifier":"00000000118-0003",
         "refNo":null,"submitDate":"","submitTime":" "}],"errors":[]}},
         "messages":[],"errors":[{"type":"FIELD","code":"personalInformation.field.required","targets":[]}]}
        """));
  }
}
