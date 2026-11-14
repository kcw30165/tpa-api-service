package com.bct.ngtpa.apiservice.adapter.in.web.vestingscale;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response.OverviewMemberResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response.OverviewPlanResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response.OverviewResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response.PlanServiceResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response.PlanTypeOptionResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response.ResponseField;
import com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response.VestingScaleDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response.VestingScalePlanResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response.VestingScaleResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response.VestingScaleRowResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response.VestingScaleSectionResponse;
import com.bct.ngtpa.apiservice.infrastructure.jackson.JacksonConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.EncoderHttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    classes = VestingScaleResponseSerializationContractTest.CodecTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.config.name=gc-vs-008-codec-test-isolated",
      "spring.main.web-application-type=reactive",
      "spring.main.banner-mode=off"
    })
class VestingScaleResponseSerializationContractTest {

  private static final String PRESENCE_JSON =
      """
      {"data":{"planTypes":[{"planNo":"P1","planType":"English"}],"overview":{"member":{"memberCategory":"ACTIVE","dateJoinedCompany":null,"employmentDate":"30/04/2020"},"plans":[{"planNo":"P1","planEffectiveDate":"01/02/2019","dateOfJoiningPlan":null,"planService":{"years":2,"months":3},"vestingPeriodBasedOn":"Company date","memberContributionBasis":null,"employerContributionBasis":"5%"}]},"vestingScale":{"plans":[{"planNo":"P1","scales":[{"completedYears":1,"vestingPercentage":10.0}]}]}}}
      """
          .trim();

  private static final String EMPTY_JSON =
      """
      {"data":{"planTypes":[],"overview":{"member":{},"plans":[]}}}
      """
          .trim();

  @Autowired private ServerCodecConfigurer serverCodecConfigurer;
  @Autowired private ObjectMapper jackson2ObjectMapper;
  @Autowired private ApplicationContext applicationContext;

  @Test
  void identifiesTheApplicationAndBindToControllerJsonEncoders() throws Exception {
    JacksonJsonEncoder applicationEncoder = jsonEncoder(serverCodecConfigurer);

    AtomicReference<ServerCodecConfigurer> bindToControllerCodecs = new AtomicReference<>();
    WebTestClient.bindToController(new EncoderProbeController(null))
        .httpMessageCodecs(bindToControllerCodecs::set)
        .build();
    JacksonJsonEncoder bindToControllerEncoder = jsonEncoder(bindToControllerCodecs.get());
    JsonMapper bootMapper = (JsonMapper) applicationEncoder.getMapper();
    JsonMapper plainMapper = JsonMapper.builder().build();

    var jackson3MapperBeans =
        applicationContext.getBeansOfType(tools.jackson.databind.json.JsonMapper.class);
    boolean applicationMapperIsJackson3Bean =
        jackson3MapperBeans.values().stream()
            .anyMatch(mapper -> mapper == applicationEncoder.getMapper());

    System.out.printf(
        "application encoder=%s mapper=%s mapperIdentity=%d mapperBean=%s sameJackson2=%s%n",
        applicationEncoder.getClass().getName(),
        applicationEncoder.getMapper().getClass().getName(),
        System.identityHashCode(applicationEncoder.getMapper()),
        applicationMapperIsJackson3Bean,
        (Object) applicationEncoder.getMapper() == jackson2ObjectMapper);
    System.out.printf(
        "bindToController encoder=%s mapper=%s sameEncoderClass=%s sameMapperClass=%s%n",
        bindToControllerEncoder.getClass().getName(),
        bindToControllerEncoder.getMapper().getClass().getName(),
        applicationEncoder.getClass().equals(bindToControllerEncoder.getClass()),
        applicationEncoder
            .getMapper()
            .getClass()
            .equals(bindToControllerEncoder.getMapper().getClass()));
    System.out.printf(
        "mapper features: inclusion=%s/plain=%s, failUnknown=%s/plain=%s, failEmptyBeans=%s/plain=%s, dateString=%s/plain=%s, decimal=%s/plain=%s%n",
        bootMapper.serializationConfig().getDefaultPropertyInclusion().getValueInclusion(),
        plainMapper.serializationConfig().getDefaultPropertyInclusion().getValueInclusion(),
        bootMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES),
        plainMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES),
        bootMapper.isEnabled(SerializationFeature.FAIL_ON_EMPTY_BEANS),
        plainMapper.isEnabled(SerializationFeature.FAIL_ON_EMPTY_BEANS),
        bootMapper.writeValueAsString("30/04/2020"),
        plainMapper.writeValueAsString("30/04/2020"),
        bootMapper.writeValueAsString(new BigDecimal("10.0")),
        plainMapper.writeValueAsString(new BigDecimal("10.0")));

    assertThat(applicationEncoder.getMapper())
        .isNotSameAs((Object) jackson2ObjectMapper)
        .isInstanceOf(tools.jackson.databind.json.JsonMapper.class);
    assertThat(bindToControllerEncoder.getClass()).isEqualTo(applicationEncoder.getClass());
    assertThat(bindToControllerEncoder.getMapper().getClass())
        .isEqualTo(applicationEncoder.getMapper().getClass());
  }

  @Test
  void serializesAllPresenceStatesAndNumericPercentagesThroughBothEncoders() {
    VestingScaleResultResponse response = populatedResponse();

    assertThat(encode(applicationEncoder(), response)).isEqualTo(PRESENCE_JSON);
    assertThat(encodeThroughBindToController(response)).isEqualTo(PRESENCE_JSON);
  }

  @Test
  void omitsAbsentVestingScaleButIncludesEmptyAndNullScaleValuesThroughBothEncoders() {
    VestingScaleResultResponse absentSection = responseWithVestingScale(ResponseField.absent());
    VestingScaleResultResponse emptySection =
        responseWithVestingScale(ResponseField.present(new VestingScaleSectionResponse(List.of())));
    VestingScaleResultResponse nullScales =
        responseWithVestingScale(
            ResponseField.present(
                new VestingScaleSectionResponse(
                    List.of(new VestingScalePlanResponse("P1", ResponseField.present(null))))));

    assertBothEncoders(
        absentSection, "{\"data\":{\"planTypes\":[],\"overview\":{\"member\":{},\"plans\":[]}}}");
    assertBothEncoders(
        emptySection,
        "{\"data\":{\"planTypes\":[],\"overview\":{\"member\":{},\"plans\":[]},\"vestingScale\":{\"plans\":[]}}}");
    assertBothEncoders(
        nullScales,
        "{\"data\":{\"planTypes\":[],\"overview\":{\"member\":{},\"plans\":[]},\"vestingScale\":{\"plans\":[{\"planNo\":\"P1\",\"scales\":null}]}}}");
  }

  @Test
  void alwaysEmitsEmptyMemberAndPlansCollections() {
    assertBothEncoders(emptyResponse(), EMPTY_JSON);
  }

  @Test
  void omitsEveryAbsentConfigurableMemberAndPlanField() {
    OverviewPlanResponse plan =
        new OverviewPlanResponse(
            "P1",
            ResponseField.absent(),
            ResponseField.absent(),
            ResponseField.absent(),
            "Company date",
            ResponseField.absent(),
            ResponseField.absent());
    VestingScaleResultResponse response =
        new VestingScaleResultResponse(
            new VestingScaleDataResponse(
                List.of(),
                new OverviewResponse(new OverviewMemberResponse(), List.of(plan)),
                ResponseField.absent()));

    assertBothEncoders(
        response,
        "{\"data\":{\"planTypes\":[],\"overview\":{\"member\":{},\"plans\":[{\"planNo\":\"P1\",\"vestingPeriodBasedOn\":\"Company date\"}]}}}");
  }

  @Test
  void preservesPresentNullForEveryConfigurableMemberAndPlanField() {
    OverviewMemberResponse member =
        new OverviewMemberResponse(
            ResponseField.present(null),
            ResponseField.present(null),
            ResponseField.present(null),
            ResponseField.present(null));
    OverviewPlanResponse plan =
        new OverviewPlanResponse(
            "P1",
            ResponseField.present(null),
            ResponseField.present(null),
            ResponseField.present(null),
            "Company date",
            ResponseField.present(null),
            ResponseField.present(null));
    VestingScaleResultResponse response =
        new VestingScaleResultResponse(
            new VestingScaleDataResponse(
                List.of(),
                new OverviewResponse(member, List.of(plan)),
                ResponseField.present(
                    new VestingScaleSectionResponse(
                        List.of(
                            new VestingScalePlanResponse("P1", ResponseField.present(null)))))));

    assertBothEncoders(
        response,
        "{\"data\":{\"planTypes\":[],\"overview\":{\"member\":{\"memberCategory\":null,\"dateJoinedCompany\":null,\"dateJoinedScheme\":null,\"employmentDate\":null},\"plans\":[{\"planNo\":\"P1\",\"planEffectiveDate\":null,\"dateOfJoiningPlan\":null,\"planService\":null,\"vestingPeriodBasedOn\":\"Company date\",\"memberContributionBasis\":null,\"employerContributionBasis\":null}]},\"vestingScale\":{\"plans\":[{\"planNo\":\"P1\",\"scales\":null}]}}}");
  }

  private void assertBothEncoders(VestingScaleResultResponse response, String expected) {
    assertThat(encode(applicationEncoder(), response)).isEqualTo(expected);
    assertThat(encodeThroughBindToController(response)).isEqualTo(expected);
  }

  private JacksonJsonEncoder applicationEncoder() {
    return jsonEncoder(serverCodecConfigurer);
  }

  private String encodeThroughBindToController(VestingScaleResultResponse response) {
    WebTestClient client =
        WebTestClient.bindToController(new EncoderProbeController(response)).build();
    return client
        .get()
        .uri("/vesting-scale-response")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .returnResult()
        .getResponseBody();
  }

  private static String encode(JacksonJsonEncoder encoder, Object value) {
    DataBuffer buffer =
        encoder.encodeValue(
            value,
            DefaultDataBufferFactory.sharedInstance,
            ResolvableType.forInstance(value),
            MediaType.APPLICATION_JSON,
            Map.of());
    try {
      return buffer.toString(StandardCharsets.UTF_8);
    } finally {
      DataBufferUtils.release(buffer);
    }
  }

  private static VestingScaleResultResponse populatedResponse() {
    OverviewMemberResponse member =
        new OverviewMemberResponse(
            ResponseField.present("ACTIVE"),
            ResponseField.present(null),
            ResponseField.absent(),
            ResponseField.present("30/04/2020"));
    OverviewPlanResponse plan =
        new OverviewPlanResponse(
            "P1",
            ResponseField.present("01/02/2019"),
            ResponseField.present(null),
            ResponseField.present(new PlanServiceResponse(2, 3)),
            "Company date",
            ResponseField.present(null),
            ResponseField.present("5%"));
    VestingScaleSectionResponse scale =
        new VestingScaleSectionResponse(
            List.of(
                new VestingScalePlanResponse(
                    "P1",
                    ResponseField.present(
                        List.of(new VestingScaleRowResponse(1, new BigDecimal("10.0")))))));
    return new VestingScaleResultResponse(
        new VestingScaleDataResponse(
            List.of(new PlanTypeOptionResponse("P1", "English")),
            new OverviewResponse(member, List.of(plan)),
            ResponseField.present(scale)));
  }

  private static VestingScaleResultResponse emptyResponse() {
    return responseWithVestingScale(ResponseField.absent());
  }

  private static VestingScaleResultResponse responseWithVestingScale(
      ResponseField<VestingScaleSectionResponse> vestingScale) {
    return new VestingScaleResultResponse(
        new VestingScaleDataResponse(
            List.of(),
            new OverviewResponse(new OverviewMemberResponse(), List.of()),
            vestingScale));
  }

  private static JacksonJsonEncoder jsonEncoder(ServerCodecConfigurer configurer) {
    return configurer.getWriters().stream()
        .filter(EncoderHttpMessageWriter.class::isInstance)
        .map(writer -> ((EncoderHttpMessageWriter<?>) writer).getEncoder())
        .filter(JacksonJsonEncoder.class::isInstance)
        .map(JacksonJsonEncoder.class::cast)
        .findFirst()
        .orElseThrow();
  }

  @RestController
  static class EncoderProbeController {
    private final VestingScaleResultResponse response;

    EncoderProbeController(VestingScaleResultResponse response) {
      this.response = response;
    }

    @GetMapping("/encoder-probe")
    String probe() {
      return "ok";
    }

    @GetMapping("/vesting-scale-response")
    VestingScaleResultResponse vestingScaleResponse() {
      return response;
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      excludeName = {
        "org.springframework.boot.micrometer.metrics.autoconfigure.ssl.SslMetricsAutoConfiguration"
      })
  @Import(JacksonConfig.class)
  static class CodecTestApplication {}
}
