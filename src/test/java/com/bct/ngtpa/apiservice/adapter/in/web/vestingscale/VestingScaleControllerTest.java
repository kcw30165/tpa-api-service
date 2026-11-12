package com.bct.ngtpa.apiservice.adapter.in.web.vestingscale;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.advice.ApiExceptionHandler;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieReader;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorEventLogger;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorResponseRenderer;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMappingRegistryConfig;
import com.bct.ngtpa.apiservice.adapter.in.web.error.PublicMessageResolver;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.GetVestingScaleCommand;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.Presence;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleMember;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScalePlan;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScalePlanService;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleResult;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleRow;
import com.bct.ngtpa.apiservice.application.exception.VestingScaleRetrievalException;
import com.bct.ngtpa.apiservice.application.port.in.GetVestingScaleUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class VestingScaleControllerTest {

  private static final String ROUTE = "/api/v1/vesting-scale";
  private static final AuthenticatedSessionId SESSION_ID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");

  @Test
  void returnsTheExactEnglishEnvelopeAndBuildsCommandFromResolvedContext() {
    AtomicReference<GetVestingScaleCommand> captured = new AtomicReference<>();
    WebTestClient client =
        client(
            command -> {
              captured.set(command);
              return Mono.just(result());
            },
            context("ACC-1"),
            exchange -> Optional.of(SESSION_ID));

    client
        .get()
        .uri(ROUTE)
        .header("Accept-Language", "en")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
              "success": true,
              "status": "SUCCESS",
              "result": {
                "data": {
                  "planTypes": [{"planNo":"P1","planType":"English plan"}],
                  "overview": {
                    "member": {
                      "memberCategory": "ACTIVE",
                      "dateJoinedCompany": "01/10/1995",
                      "dateJoinedScheme": null
                    },
                    "plans": [{
                      "planNo":"P1",
                      "planType":"English plan",
                      "planEffectiveDate":"01/02/2019",
                      "dateOfJoiningPlan":null,
                      "planService":{"years":2,"months":3},
                      "vestingPeriodBasedOn":"English vesting basis",
                      "memberContributionBasis":null,
                      "employerContributionBasis":"English employer basis"
                    }]
                  },
                  "vestingScale": {
                    "plans": [{
                      "planNo":"P1",
                      "scales":[
                        {"completedYears":1,"vestingPercentage":10.0},
                        {"completedYears":2,"vestingPercentage":20.0}
                      ]
                    }]
                  }
                }
              },
              "messages": [],
              "errors": []
            }
            """,
            JsonCompareMode.STRICT);

    assertThat(captured.get())
        .isEqualTo(
            new GetVestingScaleCommand(
                "HK", "policy-1", "cert-1", "actor-1", ActorUserRole.ROLE_USER_MBR));
  }

  @Test
  void usesChineseValuesForZhHk() {
    WebTestClient client =
        client(
            command -> Mono.just(result()), context("ACC-1"), exchange -> Optional.of(SESSION_ID));

    client
        .get()
        .uri(ROUTE)
        .header("Accept-Language", "zh-HK")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.result.data.planTypes[0].planType")
        .isEqualTo("中文計劃")
        .jsonPath("$.result.data.overview.plans[0].vestingPeriodBasedOn")
        .isEqualTo("中文歸屬基準")
        .jsonPath("$.result.data.overview.plans[0].memberContributionBasis")
        .isEqualTo("中文僱員供款基準");
  }

  @Test
  void missingAccountRefAndSessionUseExistingSelectedAccountFailures() {
    WebTestClient missingAccountRef =
        client(
            command -> Mono.error(new AssertionError("use case must not run")),
            context(" "),
            exchange -> Optional.of(SESSION_ID));
    missingAccountRef
        .get()
        .uri(ROUTE)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors[0].code")
        .isEqualTo(ErrorCodes.MEMBER_CONTEXT_INVALID);

    WebTestClient missingSession =
        client(
            command -> Mono.error(new AssertionError("use case must not run")),
            context("ACC-1"),
            exchange -> Optional.empty());
    missingSession
        .get()
        .uri(ROUTE)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.UNAUTHORIZED)
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(false)
        .jsonPath("$.status")
        .isEqualTo("BUSINESS_REJECTED")
        .jsonPath("$.errors[0].code")
        .isEqualTo(ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED);
  }

  @Test
  void retrievalFailureReturnsSafe503WithoutDownstreamCode() {
    WebTestClient client =
        client(
            command -> Mono.error(VestingScaleRetrievalException.systemFailure()),
            context("ACC-1"),
            exchange -> Optional.of(SESSION_ID));

    String body =
        client
            .get()
            .uri(ROUTE)
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    assertThat(body)
        .isEqualTo(
            "{\"success\":false,\"status\":\"SYSTEM_ERROR\",\"result\":null,\"messages\":[],\"errors\":[{\"type\":\"SYSTEM\",\"code\":\"err.vesting-scale.retrieval.unavailable\",\"targets\":[],\"severity\":\"ERROR\",\"source\":\"SERVER\"}]}");
    assertThat(body).doesNotContain("err.tpa.login.user.id.invalid", "err.tpa.cert.no.required");
  }

  private static WebTestClient client(
      GetVestingScaleUseCase useCase,
      PortalAccessContext context,
      AuthenticatedSessionCookieReader sessionCookieReader) {
    return WebTestClient.bindToController(
            new VestingScaleController(
                useCase, resolver(context), sessionCookieReader, new VestingScaleWebMapper()))
        .controllerAdvice(advice())
        .build();
  }

  private static CurrentPortalAccessContextResolver resolver(PortalAccessContext context) {
    return new CurrentPortalAccessContextResolver() {
      @Override
      public Mono<PortalAccessContext> current() {
        return Mono.just(context);
      }

      @Override
      public Mono<PortalAccessContext> currentOrEmpty() {
        return Mono.just(context);
      }
    };
  }

  private static PortalAccessContext context(String accountRef) {
    return new PortalAccessContext(
        new ActorContext("actor-1", ActorUserRole.ROLE_USER_MBR),
        new AccountContext(
            accountRef, "HK", "policy-1", "cert-1", "TRUST-1", "SCHEME-1", TermStatus.BLANK, null));
  }

  private static VestingScaleResult result() {
    return new VestingScaleResult(
        new VestingScaleMember(
            Presence.present("ACTIVE"),
            Presence.present(LocalDate.of(1995, 10, 1)),
            Presence.present(null),
            Presence.absent()),
        List.of(
            new VestingScalePlan(
                "P1",
                "English plan",
                "中文計劃",
                Presence.present(LocalDate.of(2019, 2, 1)),
                Presence.present(null),
                Presence.present(new VestingScalePlanService(2, 3)),
                Presence.present("English vesting basis"),
                Presence.present("中文歸屬基準"),
                Presence.present(null),
                Presence.present("中文僱員供款基準"),
                Presence.present("English employer basis"),
                Presence.present("中文僱主供款基準"),
                Presence.present(
                    List.of(
                        new VestingScaleRow(2, new BigDecimal("20.0")),
                        new VestingScaleRow(1, new BigDecimal("10.0")))))),
        true);
  }

  private static ApiExceptionHandler advice() {
    ErrorMessageResolver resolver = (code, locale, env, trust, scheme) -> code;
    ObjectMapper objectMapper = new ObjectMapper();
    return new ApiExceptionHandler(
        new ExceptionMappingRegistryConfig().exceptionMappingRegistry(),
        new PublicMessageResolver(resolver),
        new ErrorEventLogger(
            new LoggingSanitizer(objectMapper, new MaskingService(new MaskingProperties()))),
        new ErrorResponseRenderer());
  }
}
