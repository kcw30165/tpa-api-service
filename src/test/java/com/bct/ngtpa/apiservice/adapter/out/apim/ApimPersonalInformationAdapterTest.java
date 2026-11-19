package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetMemberInfoApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetMemberInfoDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.MemberInfoConfigItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.FetchMemberInfoCommand;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class ApimPersonalInformationAdapterTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void sendsExactTypedAuthenticatedExchangeExactlyOnceAndMapsValidResponse() {
    AtomicReference<ApimExchangeRequest<?, ?>> captured = new AtomicReference<>();
    AtomicInteger calls = new AtomicInteger();

    var result =
        adapter(capturing(captured, calls, validEnvelope()))
            .fetchMemberInfo(command("JP", ActorUserRole.ROLE_USER_EPR))
            .block();
    ApimExchangeRequest<?, ?> exchange = captured.get();
    GetMemberInfoApimRequest body = (GetMemberInfoApimRequest) exchange.request();

    assertThat(calls).hasValue(1);
    assertThat(exchange.path()).isEqualTo("/ws/NGTPA/v1/TRPGetMemberInfo");
    assertThat(exchange.requestType()).isEqualTo(GetMemberInfoApimRequest.class);
    assertThat(exchange.responseDataType()).isEqualTo(GetMemberInfoDataItem.class);
    assertThat(exchange.databaseScope()).isEqualTo(ApimDatabaseScope.ACCOUNT_ENV);
    assertThat(exchange.credentialContext().actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_EPR);
    assertThat(exchange.credentialContext().accountEnv()).isEqualTo("JP");
    assertThat(exchange.retryEligibility()).isEqualTo(ApimRetryEligibility.SAFE_BUSINESS_CALL);
    assertThat(body.getPolicyNo()).isEqualTo("POL-1");
    assertThat(body.getCertNo()).isEqualTo("CERT-1");
    assertThat(body.getLoginUserId()).isEqualTo("USER-1");
    assertThat(result.getPayload()).containsOnlyKeys("config", "configItemTypes", "data");
    Map<?, ?> config = (Map<?, ?>) result.getPayload().get("config");
    Map<?, ?> data = (Map<?, ?>) result.getPayload().get("data");
    assertThat(config.get("addr1")).isEqualTo("EDITABLE_COM");
    assertThat(data.get("addr1")).isEqualTo("1 Example Street");
  }

  @Test
  void serializedRequestContainsExactTrpGetMemberInfoFieldsWithoutEnvAndNoFrontendAuthority()
      throws Exception {
    AtomicReference<ApimExchangeRequest<?, ?>> captured = new AtomicReference<>();
    adapter(capturing(captured, new AtomicInteger(), emptyEnvelope()))
        .fetchMemberInfo(command("JP", ActorUserRole.ROLE_USER_MBR))
        .block();

    var node = JSON.readTree(JSON.writeValueAsString(captured.get().request()));
    assertThat(node.get("policy-no").asText()).isEqualTo("POL-1");
    assertThat(node.get("cert-no").asText()).isEqualTo("CERT-1");
    assertThat(node.get("login-user-id").asText()).isEqualTo("USER-1");
    assertThat(node.get("login-user-role").asText()).isEqualTo("role_user_mbr");
    assertThat(node.size()).isEqualTo(4);
    assertThat(node.has("env")).isFalse();
    assertThat(node.has("accountEnv")).isFalse();
    assertThat(node.has("accountRef")).isFalse();
    assertThat(node.has("actorUserRole")).isFalse();
    assertThat(node.has("databaseScope")).isFalse();
    assertThat(node.has("retryEligibility")).isFalse();
    assertThat(node.has("user-id")).isFalse();
    assertThat(node.has("user-role")).isFalse();
    assertThat(node.has("mbr-type")).isFalse();
  }

  @Test
  void preservesCommandActorRoleAndAccountEnvironmentInExchange() {
    AtomicReference<ApimExchangeRequest<?, ?>> captured = new AtomicReference<>();
    AtomicInteger calls = new AtomicInteger();
    adapter(capturing(captured, calls, emptyEnvelope()))
        .fetchMemberInfo(command("UAT", ActorUserRole.ROLE_USER_EPR))
        .block();

    assertThat(captured.get().credentialContext().actorUserRole())
        .isEqualTo(ActorUserRole.ROLE_USER_EPR);
    assertThat(captured.get().credentialContext().accountEnv()).isEqualTo("UAT");
    assertThat(calls).hasValue(1);
  }

  @Test
  void blankAccountEnvironmentFailsBeforeExecutorInvocation() {
    AtomicInteger calls = new AtomicInteger();
    assertThatThrownBy(
            () ->
                adapter(countingFailure(calls, new AssertionError("must not run")))
                    .fetchMemberInfo(command(" ", ActorUserRole.ROLE_USER_MBR))
                    .block())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ACCOUNT_ENV");
    assertThat(calls).hasValue(0);
  }

  @Test
  void emptyNoRecordResponseReturnsEmptyPayload() {
    var result =
        adapter(fixed(emptyEnvelope()))
            .fetchMemberInfo(command("JP", ActorUserRole.ROLE_USER_MBR))
            .block();
    assertThat(result.getPayload()).isEmpty();
  }

  @Test
  void businessRejectionRemainsFeatureOwned() {
    assertThatThrownBy(
            () ->
                adapter(fixed(errorEnvelope("APIM member lookup failed")))
                    .fetchMemberInfo(command("JP", ActorUserRole.ROLE_USER_MBR))
                    .block())
        .isInstanceOf(ApimException.class)
        .hasMessage("APIM member lookup failed")
        .extracting("errorCode")
        .isEqualTo(ErrorCodes.APIM_RESPONSE_INVALID);
  }

  @Test
  void malformedAndStructurallyInvalidEnvelopesRemainFeatureOwned() {
    assertThatThrownBy(
            () ->
                adapter(fixed(envelopeWithMissingResponse()))
                    .fetchMemberInfo(command("JP", ActorUserRole.ROLE_USER_MBR))
                    .block())
        .isInstanceOf(ApimException.class)
        .hasMessageContaining("response is missing");
    assertThatThrownBy(
            () ->
                adapter(fixed(envelopeWithMissingData()))
                    .fetchMemberInfo(command("JP", ActorUserRole.ROLE_USER_MBR))
                    .block())
        .isInstanceOf(ApimException.class)
        .hasMessageContaining("response.data is required");
  }

  @Test
  void ordinaryExecutorFailurePropagatesUnchanged() {
    IllegalStateException failure = new IllegalStateException("executor unavailable");
    assertThatThrownBy(
            () ->
                adapter(countingFailure(new AtomicInteger(), failure))
                    .fetchMemberInfo(command("JP", ActorUserRole.ROLE_USER_MBR))
                    .block())
        .isSameAs(failure);
  }

  @Test
  void nullConfigDataAndItemsPreserveDefensiveMappingBehavior() {
    ArrayList<MemberInfoConfigItem> config = new ArrayList<>();
    config.add(null);
    config.add(MemberInfoConfigItem.builder().itemId(null).configValue("ignored").build());
    config.add(MemberInfoConfigItem.builder().itemId("email").configValue("READONLY").build());
    ArrayList<Map<String, Object>> data = new ArrayList<>();
    data.add(null);
    var envelope = success(GetMemberInfoDataItem.builder().config(config).data(data).build());

    var result =
        adapter(fixed(envelope))
            .fetchMemberInfo(command("JP", ActorUserRole.ROLE_USER_MBR))
            .block();
    Map<?, ?> mappedConfig = (Map<?, ?>) result.getPayload().get("config");
    assertThat(mappedConfig).hasSize(1);
    assertThat(mappedConfig.containsKey("email")).isTrue();
    assertThat((Map<?, ?>) result.getPayload().get("data")).isEmpty();

    var nullItemResult =
        adapter(fixed(success((GetMemberInfoDataItem) null)))
            .fetchMemberInfo(command("JP", ActorUserRole.ROLE_USER_MBR))
            .block();
    assertThat((Map<?, ?>) nullItemResult.getPayload().get("config")).isEmpty();
    assertThat((Map<?, ?>) nullItemResult.getPayload().get("data")).isEmpty();
  }

  @Test
  void filtersRawMatrixBeforeTypedMappingAndKeepsOnlyNormalizedUiAndDataRows() {
    AtomicReference<ApimExchangeRequest<?, ?>> captured = new AtomicReference<>();
    String responseJson =
        """
        {"response":{"err-message":"","data":[{
          "config":[
            {"item-id":"ui","item-type":"UI","config-value":"READONLY"},
            {"item-id":"lower-ui","item-type":"ui","config-value":"EDITABLE_COM"},
            {"item-id":"padded-ui","item-type":" UI ","config-value":"EDITABLE_OPTION"},
            {"item-id":"mixed-case-ui","item-type":"uI","config-value":"NOT_AN_ACCESS_VALUE"},
            {"item-id":"data","item-type":"data","config-value":"READONLY"},
            {"item-id":"mixed-data","item-type":" DaTa ","config-value":"EDITABLE_COM"},
            {"item-id":"invalid-string","item-type":"CONFIG","config-value":"NOT_AN_ACCESS_VALUE"},
            {"item-id":"object-config","item-type":"CONFIG","config-value":{"bad":[1]}},
            {"item-id":"array-rule","item-type":"RULE","config-value":["bad"]},
            {"item-id":"unknown","item-type":"UNKNOWN","config-value":false},
            {"item-id":"other","item-type":"OTHER","config-value":17},
            {"item-id":"missing-type","config-value":{"bad":true}},
            {"item-id":"null-type","item-type":null,"config-value":null},
            {"item-id":"numeric-type","item-type":7,"config-value":[]},
            {"item-id":"object-type","item-type":{"name":"UI"},"config-value":"READONLY"},
            {"item-id":"blank-type","item-type":"  ","config-value":["bad"]},
            null,
            7,
            {"item-id":"duplicate","item-type":"UI","config-value":"READONLY"},
            {"item-id":"duplicate","item-type":"CONFIG","config-value":{"bad":true}},
            {"item-id":"duplicate","item-type":" DATA ","config-value":"EDITABLE_OPTION"}
          ],
          "data":[{"email":"a@b.test"}]
        }]}}
        """;

    var result =
        adapter(rawJsonResponse(captured, responseJson))
            .fetchMemberInfo(command("JP", ActorUserRole.ROLE_USER_MBR))
            .block();

    assertThat(captured.get().responseDataType()).isEqualTo(GetMemberInfoDataItem.class);
    assertThat(stringObjectMap(result.getPayload().get("config")))
        .containsOnlyKeys(
            "ui", "lower-ui", "padded-ui", "mixed-case-ui", "data", "mixed-data", "duplicate")
        .containsEntry("ui", "READONLY")
        .containsEntry("lower-ui", "EDITABLE_COM")
        .containsEntry("padded-ui", "EDITABLE_OPTION")
        .containsEntry("mixed-case-ui", "NOT_AN_ACCESS_VALUE")
        .containsEntry("data", "READONLY")
        .containsEntry("mixed-data", "EDITABLE_COM")
        .containsEntry("duplicate", "EDITABLE_OPTION");
    assertThat(stringMap(result.getPayload().get("configItemTypes")))
        .containsOnlyKeys(
            "ui", "lower-ui", "padded-ui", "mixed-case-ui", "data", "mixed-data", "duplicate")
        .containsEntry("ui", "UI")
        .containsEntry("lower-ui", "ui")
        .containsEntry("padded-ui", " UI ")
        .containsEntry("mixed-case-ui", "uI")
        .containsEntry("data", "data")
        .containsEntry("mixed-data", " DaTa ")
        .containsEntry("duplicate", " DATA ");
    assertThat(stringObjectMap(result.getPayload().get("data"))).containsEntry("email", "a@b.test");
  }

  @Test
  void suppliedTrpGetMemberInfoResponseDeserializesAndMapsAllConfigurationEntries() {
    AtomicReference<ApimExchangeRequest<?, ?>> captured = new AtomicReference<>();
    String responseJson =
        """
        {"response":{"err-message":"","data":[{"config":[
          {
            "item-id": "check-box-apply-all-member",
            "item-name": "Check box of apply all member",
            "item-type": "UI",
            "function": "INFO_UPDATE",
            "sch-type": "Any",
            "config-value": "EDITABLE_OPTION"
          },
          {
            "item-id": "check-box-of-same-as-residential-address",
            "item-name": "Check box of Same as Residential Address",
            "item-type": "UI",
            "function": "INFO_UPDATE",
            "sch-type": "Any",
            "config-value": "EDITABLE_OPTION"
          },
          {
            "item-id": "addr1",
            "item-name": "Residential Address 1",
            "item-type": "DATA",
            "function": "INFO_UPDATE",
            "sch-type": "Any",
            "config-value": "EDITABLE_COM"
          },
          {
            "item-id": "addr2",
            "item-name": "Residential Address 2",
            "item-type": "DATA",
            "function": "INFO_UPDATE",
            "sch-type": "Any",
            "config-value": "EDITABLE_OPTION"
          },
          {
            "item-id": "addr3",
            "item-name": "Residential Address 3",
            "item-type": "DATA",
            "function": "INFO_UPDATE",
            "sch-type": "Any",
            "config-value": "EDITABLE_OPTION"
          },
          {
            "item-id": "country",
            "item-name": "Residential Country",
            "item-type": "DATA",
            "function": "INFO_UPDATE",
            "sch-type": "Any",
            "config-value": "EDITABLE_COM"
          },
          {
            "item-id": "corr-addr1",
            "item-name": "Correspondence Address 1",
            "item-type": "DATA",
            "function": "INFO_UPDATE",
            "sch-type": "Any",
            "config-value": "EDITABLE_COM"
          },
          {
            "item-id": "corr-addr2",
            "item-name": "Correspondence Address 2",
            "item-type": "DATA",
            "function": "INFO_UPDATE",
            "sch-type": "Any",
            "config-value": "EDITABLE_OPTION"
          },
          {
            "item-id": "corr-addr3",
            "item-name": "Correspondence Address 3",
            "item-type": "DATA",
            "function": "INFO_UPDATE",
            "sch-type": "Any",
            "config-value": "EDITABLE_OPTION"
          },
          {
            "item-id": "corr-country",
            "item-name": "Correspondence Country",
            "item-type": "DATA",
            "function": "INFO_UPDATE",
            "sch-type": "Any",
            "config-value": "EDITABLE_COM"
          },
          {
            "item-id": "business-phone",
            "item-name": "Hong Kong Business Phone (Phone Number)",
            "item-type": "DATA",
            "function": "INFO_UPDATE",
            "sch-type": "Any",
            "config-value": "EDITABLE_OPTION"
          },
          {
            "item-id": "business-phone-ext",
            "item-name": "Hong Kong Business Phone (Extension)",
            "item-type": "DATA",
            "function": "INFO_UPDATE",
            "sch-type": "Any",
            "config-value": "EDITABLE_OPTION"
          },
          {
            "item-id": "mobile-number",
            "item-name": "Hong Kong Mobile Phone",
            "item-type": "DATA",
            "function": "INFO_UPDATE",
            "sch-type": "Any",
            "config-value": "EDITABLE_OPTION"
          },
          {
            "item-id": "home-phone",
            "item-name": "Home Phone",
            "item-type": "DATA",
            "function": "INFO_UPDATE",
            "sch-type": "Any",
            "config-value": "EDITABLE_OPTION"
          },
          {
            "item-id": "other-phone",
            "item-name": "Overseas Contact (Phone Number)",
            "item-type": "DATA",
            "function": "INFO_UPDATE",
            "sch-type": "Any",
            "config-value": "EDITABLE_OPTION"
          },
          {
            "item-id": "other-phone-area",
            "item-name": "Overseas Contact (Area Code)",
            "item-type": "DATA",
            "function": "INFO_UPDATE",
            "sch-type": "Any",
            "config-value": "EDITABLE_OPTION"
          },
          {
            "item-id": "other-phone-country",
            "item-name": "Overseas Contact (Country Code)",
            "item-type": "DATA",
            "function": "INFO_UPDATE",
            "sch-type": "Any",
            "config-value": "EDITABLE_OPTION"
          },
          {
            "item-id": "other-phone-ext",
            "item-name": "Overseas Contact (Extension)",
            "item-type": "DATA",
            "function": "INFO_UPDATE",
            "sch-type": "Any",
            "config-value": "EDITABLE_OPTION"
          },
          {
            "item-id": "fax",
            "item-name": "Fax Number",
            "item-type": "DATA",
            "function": "INFO_UPDATE",
            "sch-type": "Any",
            "config-value": "EDITABLE_OPTION"
          },
          {
            "item-id": "email",
            "item-name": "Email Contact",
            "item-type": "DATA",
            "function": "INFO_UPDATE",
            "sch-type": "Any",
            "config-value": "EDITABLE_COM"
          },
          {
            "item-id": "sms-language",
            "item-name": "Language Selection of SMS",
            "item-type": "DATA",
            "function": "INFO_UPDATE",
            "sch-type": "Any",
            "config-value": "EDITABLE_OPTION"
          },
          {
            "item-id": "important-notes-section",
            "item-name": "Important Notes Section",
            "item-type": "UI",
            "function": "INFO_UPDATE",
            "sch-type": "Any",
            "config-value": "READONLY"
          }
        ],"data":[
          {
            "addr1": "BUILDING",
            "addr2": "AAA BBB CCC",
            "addr3": "DISTRICT",
            "country": "Local",
            "corr-addr1": "",
            "corr-addr2": "",
            "corr-addr3": "",
            "corr-country": "",
            "business-phone": "",
            "business-phone-ext": "",
            "mobile-number": "94227617",
            "home-phone": "21234567",
            "other-phone": "61234567",
            "other-phone-area": "11",
            "other-phone-country": "22",
            "other-phone-ext": "",
            "fax": "31245678",
            "email": "YYPH.OJ@LH.WWN",
            "sms-language": ""
          }
        ]}]}}
        """;

    var result =
        adapter(rawJsonResponse(captured, responseJson))
            .fetchMemberInfo(command("JP", ActorUserRole.ROLE_USER_MBR))
            .block();

    assertThat(captured.get().responseDataType()).isEqualTo(GetMemberInfoDataItem.class);
    assertThat(stringObjectMap(result.getPayload().get("config")))
        .isEqualTo(
            Map.ofEntries(
                Map.entry("check-box-apply-all-member", "EDITABLE_OPTION"),
                Map.entry("check-box-of-same-as-residential-address", "EDITABLE_OPTION"),
                Map.entry("addr1", "EDITABLE_COM"),
                Map.entry("addr2", "EDITABLE_OPTION"),
                Map.entry("addr3", "EDITABLE_OPTION"),
                Map.entry("country", "EDITABLE_COM"),
                Map.entry("corr-addr1", "EDITABLE_COM"),
                Map.entry("corr-addr2", "EDITABLE_OPTION"),
                Map.entry("corr-addr3", "EDITABLE_OPTION"),
                Map.entry("corr-country", "EDITABLE_COM"),
                Map.entry("business-phone", "EDITABLE_OPTION"),
                Map.entry("business-phone-ext", "EDITABLE_OPTION"),
                Map.entry("mobile-number", "EDITABLE_OPTION"),
                Map.entry("home-phone", "EDITABLE_OPTION"),
                Map.entry("other-phone", "EDITABLE_OPTION"),
                Map.entry("other-phone-area", "EDITABLE_OPTION"),
                Map.entry("other-phone-country", "EDITABLE_OPTION"),
                Map.entry("other-phone-ext", "EDITABLE_OPTION"),
                Map.entry("fax", "EDITABLE_OPTION"),
                Map.entry("email", "EDITABLE_COM"),
                Map.entry("sms-language", "EDITABLE_OPTION"),
                Map.entry("important-notes-section", "READONLY")));
    assertThat(stringMap(result.getPayload().get("configItemTypes")))
        .isEqualTo(
            Map.ofEntries(
                Map.entry("check-box-apply-all-member", "UI"),
                Map.entry("check-box-of-same-as-residential-address", "UI"),
                Map.entry("addr1", "DATA"),
                Map.entry("addr2", "DATA"),
                Map.entry("addr3", "DATA"),
                Map.entry("country", "DATA"),
                Map.entry("corr-addr1", "DATA"),
                Map.entry("corr-addr2", "DATA"),
                Map.entry("corr-addr3", "DATA"),
                Map.entry("corr-country", "DATA"),
                Map.entry("business-phone", "DATA"),
                Map.entry("business-phone-ext", "DATA"),
                Map.entry("mobile-number", "DATA"),
                Map.entry("home-phone", "DATA"),
                Map.entry("other-phone", "DATA"),
                Map.entry("other-phone-area", "DATA"),
                Map.entry("other-phone-country", "DATA"),
                Map.entry("other-phone-ext", "DATA"),
                Map.entry("fax", "DATA"),
                Map.entry("email", "DATA"),
                Map.entry("sms-language", "DATA"),
                Map.entry("important-notes-section", "UI")));
    assertThat(stringObjectMap(result.getPayload().get("data")))
        .isEqualTo(
            Map.ofEntries(
                Map.entry("addr1", "BUILDING"),
                Map.entry("addr2", "AAA BBB CCC"),
                Map.entry("addr3", "DISTRICT"),
                Map.entry("country", "Local"),
                Map.entry("corr-addr1", ""),
                Map.entry("corr-addr2", ""),
                Map.entry("corr-addr3", ""),
                Map.entry("corr-country", ""),
                Map.entry("business-phone", ""),
                Map.entry("business-phone-ext", ""),
                Map.entry("mobile-number", "94227617"),
                Map.entry("home-phone", "21234567"),
                Map.entry("other-phone", "61234567"),
                Map.entry("other-phone-area", "11"),
                Map.entry("other-phone-country", "22"),
                Map.entry("other-phone-ext", ""),
                Map.entry("fax", "31245678"),
                Map.entry("email", "YYPH.OJ@LH.WWN"),
                Map.entry("sms-language", "")));
  }

  @Test
  void retainedRowsKeepTypedConfigValueValidation() {
    String responseJson =
        """
        {"response":{"err-message":"","data":[{
          "config":[{"item-id":"ui","item-type":"UI","config-value":{"invalid":true}}],
          "data":[{}]
        }]}}
        """;

    assertThatThrownBy(
            () ->
                adapter(rawJsonResponse(new AtomicReference<>(), responseJson))
                    .fetchMemberInfo(command("JP", ActorUserRole.ROLE_USER_MBR))
                    .block())
        .hasRootCauseInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
  }

  @Test
  void malformedConfigContainerRemainsAnApimMappingFailure() {
    String responseJson =
        """
        {"response":{"err-message":"","data":[{"config":{"item-id":"ui"},"data":[{}]}]}}
        """;

    var result =
        adapter(rawJsonResponse(new AtomicReference<>(), responseJson))
            .fetchMemberInfo(command("JP", ActorUserRole.ROLE_USER_MBR));

    assertThatThrownBy(result::block)
        .isInstanceOf(ApimResponseMappingException.class)
        .hasRootCauseInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
  }

  @Test
  void emptyAndNoEligibleMatricesProduceEmptyConfig() {
    for (String configJson :
        List.of(
            "[]",
            """
            [
              {"item-id":"config","item-type":"CONFIG","config-value":{"bad":true}},
              {"item-id":"missing-type","config-value":["bad"]},
              {"item-id":"numeric-type","item-type":5,"config-value":false}
            ]
            """)) {
      String responseJson =
          """
          {"response":{"err-message":"","data":[{"config":%s,"data":[{"email":"a@b.test"}]}]}}
          """
              .formatted(configJson);
      var result =
          adapter(rawJsonResponse(new AtomicReference<>(), responseJson))
              .fetchMemberInfo(command("JP", ActorUserRole.ROLE_USER_MBR))
              .block();

      assertThat(stringObjectMap(result.getPayload().get("config"))).isEmpty();
      assertThat(stringMap(result.getPayload().get("configItemTypes"))).isEmpty();
      assertThat(stringObjectMap(result.getPayload().get("data")))
          .containsEntry("email", "a@b.test");
    }
  }

  private static ApimExchangeExecutor rawJsonResponse(
      AtomicReference<ApimExchangeRequest<?, ?>> captured, String responseJson) {
    return new ApimExchangeExecutor() {
      @Override
      public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(ApimExchangeRequest<R, D> request) {
        captured.set(request);
        JavaType envelopeType =
            JSON.getTypeFactory()
                .constructParametricType(ApimResponseEnvelope.class, request.responseDataType());
        try {
          @SuppressWarnings("unchecked")
          ApimResponseEnvelope<D> response =
              (ApimResponseEnvelope<D>) JSON.readValue(responseJson, envelopeType);
          return Mono.just(response);
        } catch (Exception failure) {
          return Mono.error(new ApimResponseMappingException(failure));
        }
      }
    };
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> stringObjectMap(Object value) {
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, String> stringMap(Object value) {
    return (Map<String, String>) value;
  }

  private static FetchMemberInfoCommand command(String env, ActorUserRole role) {
    return new FetchMemberInfoCommand(env, "POL-1", "CERT-1", "USER-1", role);
  }

  private static ApimMemberInfoAdapter adapter(ApimExchangeExecutor executor) {
    return new ApimMemberInfoAdapter(executor);
  }

  private static ApimExchangeExecutor capturing(
      AtomicReference<ApimExchangeRequest<?, ?>> captured,
      AtomicInteger calls,
      ApimResponseEnvelope<GetMemberInfoDataItem> response) {
    return new ApimExchangeExecutor() {
      @Override
      public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(ApimExchangeRequest<R, D> request) {
        captured.set(request);
        calls.incrementAndGet();
        @SuppressWarnings("unchecked")
        ApimResponseEnvelope<D> result = (ApimResponseEnvelope<D>) (Object) response;
        return Mono.just(result);
      }
    };
  }

  private static ApimExchangeExecutor fixed(ApimResponseEnvelope<GetMemberInfoDataItem> response) {
    return capturing(new AtomicReference<>(), new AtomicInteger(), response);
  }

  private static ApimExchangeExecutor countingFailure(AtomicInteger calls, Throwable failure) {
    return new ApimExchangeExecutor() {
      @Override
      public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(ApimExchangeRequest<R, D> request) {
        calls.incrementAndGet();
        return Mono.error(failure);
      }
    };
  }

  private static ApimResponseEnvelope<GetMemberInfoDataItem> validEnvelope() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("addr1", "1 Example Street");
    return success(
        GetMemberInfoDataItem.builder()
            .config(
                List.of(
                    MemberInfoConfigItem.builder()
                        .itemId("addr1")
                        .configValue("EDITABLE_COM")
                        .build()))
            .data(List.of(data))
            .build());
  }

  private static ApimResponseEnvelope<GetMemberInfoDataItem> success(GetMemberInfoDataItem item) {
    if (item != null && item.getConfig() != null) {
      for (MemberInfoConfigItem configItem : item.getConfig()) {
        if (configItem != null) {
          configItem.setItemType("UI");
        }
      }
    }
    return ApimResponseEnvelope.<GetMemberInfoDataItem>builder()
        .response(
            ApimResponseBody.<GetMemberInfoDataItem>builder()
                .errMessage("")
                .data(java.util.Collections.singletonList(item))
                .build())
        .build();
  }

  private static ApimResponseEnvelope<GetMemberInfoDataItem> emptyEnvelope() {
    return ApimResponseEnvelope.<GetMemberInfoDataItem>builder()
        .response(
            ApimResponseBody.<GetMemberInfoDataItem>builder()
                .errMessage("")
                .data(List.of())
                .build())
        .build();
  }

  private static ApimResponseEnvelope<GetMemberInfoDataItem> errorEnvelope(String message) {
    return ApimResponseEnvelope.<GetMemberInfoDataItem>builder()
        .response(ApimResponseBody.<GetMemberInfoDataItem>builder().errMessage(message).build())
        .build();
  }

  private static ApimResponseEnvelope<GetMemberInfoDataItem> envelopeWithMissingResponse() {
    return ApimResponseEnvelope.<GetMemberInfoDataItem>builder().build();
  }

  private static ApimResponseEnvelope<GetMemberInfoDataItem> envelopeWithMissingData() {
    return ApimResponseEnvelope.<GetMemberInfoDataItem>builder()
        .response(ApimResponseBody.<GetMemberInfoDataItem>builder().errMessage("").build())
        .build();
  }
}
