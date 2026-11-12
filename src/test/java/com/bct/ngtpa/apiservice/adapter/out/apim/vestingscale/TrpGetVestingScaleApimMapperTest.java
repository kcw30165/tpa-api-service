package com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.vestingscale.Presence;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleConfigValue;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleData;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScalePlan;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TrpGetVestingScaleApimMapperTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private final TrpGetVestingScaleApimMapper mapper = new TrpGetVestingScaleApimMapper();

  @Test
  void mapsTheFullResponseAndIgnoresUnmappedWireFields() throws Exception {
    VestingScaleData data =
        mapper.map(
            JSON.readTree(
                """
                {
                  "env": "ignored-env",
                  "member": {
                    "member-category": "ACTIVE",
                    "date-joined-company": "01/10/1995",
                    "date-joined-scheme": "01/04/2000",
                    "employment-date": null
                  },
                  "plans": [{
                    "plan-no": "OCAC",
                    "plan-type-desc-eng": "Company Account",
                    "plan-type-desc-chi": "公司賬戶",
                    "plan-name": "ignored-plan-name",
                    "vest-period-base": "ignored-code",
                    "plan-effective-date": "01/02/2019",
                    "date-of-joining-plan": "02/02/2019",
                    "plan-service": {"years": 3, "months": null},
                    "vest-period-base-eng": "Date Joined Company",
                    "vest-period-base-chi": null,
                    "basis-cont-ee-eng": "Employee basis",
                    "basis-cont-ee-chi": null,
                    "basis-cont-er-eng": "Employer basis",
                    "basis-cont-er-chi": "僱主基礎",
                    "vesting-scale": [
                      {"completed-years": 2, "vest-pcnt": 20.25},
                      {"completed-years": 1, "vest-pcnt": 10}
                    ]
                  }],
                  "config": [
                    {"item-id": "member-category", "config-value": "READONLY"},
                    {"item-id": "vesting-scale", "config-value": "HIDDEN"}
                  ]
                }
                """));

    assertThat(data.member().memberCategory().value()).isEqualTo("ACTIVE");
    assertThat(data.member().dateJoinedCompany().value()).isEqualTo(LocalDate.of(1995, 10, 1));
    assertThat(data.member().dateJoinedScheme().value()).isEqualTo(LocalDate.of(2000, 4, 1));
    assertThat(data.member().employmentDate().state()).isEqualTo(Presence.State.PRESENT);
    assertThat(data.member().employmentDate().value()).isNull();
    assertThat(data.plans()).hasSize(1);
    VestingScalePlan plan = data.plans().getFirst();
    assertThat(plan.planNo()).isEqualTo("OCAC");
    assertThat(plan.planTypeDescEng()).isEqualTo("Company Account");
    assertThat(plan.planTypeDescChi()).isEqualTo("公司賬戶");
    assertThat(plan.planEffectiveDate().value()).isEqualTo(LocalDate.of(2019, 2, 1));
    assertThat(plan.dateOfJoiningPlan().value()).isEqualTo(LocalDate.of(2019, 2, 2));
    assertThat(plan.planService().value().years()).isEqualTo(3);
    assertThat(plan.planService().value().months()).isNull();
    assertThat(plan.vestPeriodBaseEng().value()).isEqualTo("Date Joined Company");
    assertThat(plan.vestPeriodBaseChi().value()).isNull();
    assertThat(plan.basisContEeEng().value()).isEqualTo("Employee basis");
    assertThat(plan.basisContEeChi().value()).isNull();
    assertThat(plan.basisContErEng().value()).isEqualTo("Employer basis");
    assertThat(plan.basisContErChi().value()).isEqualTo("僱主基礎");
    assertThat(plan.vestingScale().value())
        .extracting(row -> row.completedYears())
        .containsExactly(2, 1);
    assertThat(plan.vestingScale().value().getFirst().vestingPercentage().toPlainString())
        .isEqualTo("20.25");
    assertThat(data.config())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "member-category", VestingScaleConfigValue.READONLY,
                "vesting-scale", VestingScaleConfigValue.HIDDEN));
  }

  @Test
  void acceptsEmptyMemberPlansAndConfigAndPreservesEmptyMemberCategory() throws Exception {
    VestingScaleData empty =
        mapper.map(JSON.readTree("{\"member\": {}, \"plans\": [], \"config\": []}"));
    assertThat(empty.plans()).isEmpty();
    assertThat(empty.config()).isEmpty();
    assertThat(empty.member().memberCategory().state()).isEqualTo(Presence.State.ABSENT);

    VestingScaleData blankCategory =
        mapper.map(
            JSON.readTree(
                """
                {"member":{"member-category":""},"plans":[],"config":[]}
                """));
    assertThat(blankCategory.member().memberCategory().value()).isEmpty();
  }

  @ParameterizedTest(name = "{0}.{1} is {2}")
  @MethodSource("presenceCases")
  void preservesAbsentNullAndValueForEveryConfigurableField(
      FieldSpec field, PresenceState expectedState) throws Exception {
    ObjectNode root = minimalResponse();
    ObjectNode parent =
        field.memberField()
            ? (ObjectNode) root.get("member")
            : (ObjectNode) root.withArray("plans").get(0);
    if (expectedState == PresenceState.NULL) {
      parent.putNull(field.wireName());
    } else if (expectedState == PresenceState.VALUE) {
      parent.set(field.wireName(), JSON.readTree(field.valueJson()));
    }

    VestingScaleData data = mapper.map(root);
    Presence<?> actual = presence(data, field);

    assertThat(actual.state())
        .isEqualTo(
            expectedState == PresenceState.ABSENT ? Presence.State.ABSENT : Presence.State.PRESENT);
    if (expectedState == PresenceState.NULL) {
      assertThat(actual.value()).isNull();
    } else if (expectedState == PresenceState.VALUE) {
      assertThat(actual.value()).isNotNull();
    }
  }

  @ParameterizedTest
  @MethodSource("malformedResponses")
  void rejectsMalformedJsonKindsAndRequiredValues(String response) {
    assertThatThrownBy(() -> mapper.map(read(response)))
        .isInstanceOf(VestingScaleMalformedResponseException.class);
  }

  static Stream<Arguments> presenceCases() {
    return Stream.of(
            new FieldSpec(true, "member-category", "\"ACTIVE\""),
            new FieldSpec(true, "date-joined-company", "\"01/10/1995\""),
            new FieldSpec(true, "date-joined-scheme", "\"01/04/2000\""),
            new FieldSpec(true, "employment-date", "\"05/05/2010\""),
            new FieldSpec(false, "plan-effective-date", "\"01/02/2019\""),
            new FieldSpec(false, "date-of-joining-plan", "\"02/02/2019\""),
            new FieldSpec(false, "plan-service", "{\"years\":1,\"months\":2}"),
            new FieldSpec(false, "basis-cont-ee-eng", "\"Employee basis\""),
            new FieldSpec(false, "basis-cont-ee-chi", "\"僱員基礎\""),
            new FieldSpec(false, "basis-cont-er-eng", "\"Employer basis\""),
            new FieldSpec(false, "basis-cont-er-chi", "\"僱主基礎\""),
            new FieldSpec(false, "vesting-scale", "[]"))
        .flatMap(
            field ->
                Stream.of(PresenceState.ABSENT, PresenceState.NULL, PresenceState.VALUE)
                    .map(state -> Arguments.of(field, state)));
  }

  static Stream<String> malformedResponses() {
    return Stream.of(
        "{\"plans\":[],\"config\":[]}",
        "{\"member\":null,\"plans\":[],\"config\":[]}",
        "{\"member\":{},\"config\":[]}",
        "{\"member\":{},\"plans\":{},\"config\":[]}",
        "{\"member\":{},\"plans\":[] }",
        "{\"member\":{},\"plans\":[],\"config\":{}}",
        "{\"member\":{\"date-joined-company\":\"1995-10-01\"},\"plans\":[],\"config\":[]}",
        "{\"member\":{\"date-joined-company\":\"31/02/2020\"},\"plans\":[],\"config\":[]}",
        """
        {"member":{},"plans":[],"config":[{"item-id":"member-category","config-value":"READ_ONLY"}]}
        """,
        """
        {"member":{},"plans":[],"config":[
          {"item-id":"member-category","config-value":"READONLY"},
          {"item-id":"member-category","config-value":"HIDDEN"}]}
        """,
        "{\"member\":{},\"plans\":[],\"config\":[{\"item-id\":\" \",\"config-value\":\"HIDDEN\"}]}",
        "{\"member\":{},\"plans\":[{\"plan-no\":\" \",\"plan-type-desc-eng\":\"E\",\"plan-type-desc-chi\":\"C\"}],\"config\":[]}",
        """
        {"member":{},"plans":[
          {"plan-no":"P1","plan-type-desc-eng":"E","plan-type-desc-chi":"C"},
          {"plan-no":"P1","plan-type-desc-eng":"E","plan-type-desc-chi":"C"}],"config":[]}
        """,
        "{\"member\":{},\"plans\":[{\"plan-no\":\"P1\",\"plan-type-desc-eng\":\"\",\"plan-type-desc-chi\":\"C\"}],\"config\":[]}",
        "{\"member\":{},\"plans\":[{\"plan-no\":\"P1\",\"plan-type-desc-eng\":\"E\",\"plan-type-desc-chi\":\" \"}],\"config\":[]}",
        "{\"member\":{\"member-category\":1},\"plans\":[],\"config\":[]}",
        "{\"member\":{\"employment-date\":1},\"plans\":[],\"config\":[]}",
        """
        {"member":{},"plans":[{"plan-no":"P1","plan-type-desc-eng":"E","plan-type-desc-chi":"C",
          "plan-service":"3"}],"config":[]}
        """,
        """
        {"member":{},"plans":[{"plan-no":"P1","plan-type-desc-eng":"E","plan-type-desc-chi":"C",
          "plan-service":{"years":1.5,"months":2}}],"config":[]}
        """,
        """
        {"member":{},"plans":[{"plan-no":"P1","plan-type-desc-eng":"E","plan-type-desc-chi":"C",
          "basis-cont-ee-eng":4}],"config":[]}
        """,
        """
        {"member":{},"plans":[{"plan-no":"P1","plan-type-desc-eng":"E","plan-type-desc-chi":"C",
          "vesting-scale":{}}],"config":[]}
        """,
        """
        {"member":{},"plans":[{"plan-no":"P1","plan-type-desc-eng":"E","plan-type-desc-chi":"C",
          "vesting-scale":[{"vest-pcnt":10}]}],"config":[]}
        """,
        """
        {"member":{},"plans":[{"plan-no":"P1","plan-type-desc-eng":"E","plan-type-desc-chi":"C",
          "vesting-scale":[{"completed-years":1}]}],"config":[]}
        """,
        """
        {"member":{},"plans":[{"plan-no":"P1","plan-type-desc-eng":"E","plan-type-desc-chi":"C",
          "vesting-scale":[{"completed-years":1.0,"vest-pcnt":10}]}],"config":[]}
        """,
        """
        {"member":{},"plans":[{"plan-no":"P1","plan-type-desc-eng":"E","plan-type-desc-chi":"C",
          "vesting-scale":[{"completed-years":1,"vest-pcnt":"10"}]}],"config":[]}
        """);
  }

  private static ObjectNode minimalResponse() throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.set("member", JSON.createObjectNode());
    ObjectNode plan = root.putArray("plans").addObject();
    plan.put("plan-no", "P1");
    plan.put("plan-type-desc-eng", "English");
    plan.put("plan-type-desc-chi", "中文");
    root.putArray("config");
    return root;
  }

  private static Presence<?> presence(VestingScaleData data, FieldSpec field) {
    if (field.memberField()) {
      return switch (field.wireName()) {
        case "member-category" -> data.member().memberCategory();
        case "date-joined-company" -> data.member().dateJoinedCompany();
        case "date-joined-scheme" -> data.member().dateJoinedScheme();
        case "employment-date" -> data.member().employmentDate();
        default -> throw new IllegalArgumentException(field.wireName());
      };
    }

    VestingScalePlan plan = data.plans().getFirst();
    return switch (field.wireName()) {
      case "plan-effective-date" -> plan.planEffectiveDate();
      case "date-of-joining-plan" -> plan.dateOfJoiningPlan();
      case "plan-service" -> plan.planService();
      case "basis-cont-ee-eng" -> plan.basisContEeEng();
      case "basis-cont-ee-chi" -> plan.basisContEeChi();
      case "basis-cont-er-eng" -> plan.basisContErEng();
      case "basis-cont-er-chi" -> plan.basisContErChi();
      case "vesting-scale" -> plan.vestingScale();
      default -> throw new IllegalArgumentException(field.wireName());
    };
  }

  private static JsonNode read(String value) {
    try {
      return JSON.readTree(value);
    } catch (Exception exception) {
      throw new AssertionError(exception);
    }
  }

  private enum PresenceState {
    ABSENT,
    NULL,
    VALUE
  }

  private record FieldSpec(boolean memberField, String wireName, String valueJson) {}
}
