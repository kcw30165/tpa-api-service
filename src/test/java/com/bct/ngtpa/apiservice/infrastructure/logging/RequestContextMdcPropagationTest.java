package com.bct.ngtpa.apiservice.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.shared.web.PortalIdentityCorrelation;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

class RequestContextMdcPropagationTest {

  private static final String CONFIG_CLASS_NAME =
      "com.bct.ngtpa.apiservice.infrastructure.logging.ReactorMdcPropagationConfig";

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void propagatesRequestAndPortalIdentityContextAcrossReactorThreadHop() throws Exception {
    Class<?> configClass = Class.forName(CONFIG_CLASS_NAME);
    new ApplicationContextRunner()
        .withUserConfiguration(configClass)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              Scheduler worker = Schedulers.newSingle("mdc-propagation-test");
              try {
                Map<String, String> values =
                    Mono.just("start")
                        .publishOn(worker)
                        .map(
                            ignored ->
                                Map.of(
                                    "requestId", MDC.get("requestId"),
                                    "trustCode", MDC.get("trustCode"),
                                    "userRole", MDC.get("userRole")))
                        .contextWrite(
                            Context.of(
                                RequestCorrelation.REQUEST_ID_CONTEXT_KEY,
                                "req-123",
                                PortalIdentityCorrelation.TRUST_CODE_CONTEXT_KEY,
                                "JPM",
                                PortalIdentityCorrelation.USER_ROLE_CONTEXT_KEY,
                                "MEMBER"))
                        .block();

                assertThat(values)
                    .containsEntry("requestId", "req-123")
                    .containsEntry("trustCode", "JPM")
                    .containsEntry("userRole", "MEMBER");
              } finally {
                worker.dispose();
              }
            });
  }

  @Test
  void absentPortalIdentityDoesNotLeakPreviousMdcValues() throws Exception {
    Class<?> configClass = Class.forName(CONFIG_CLASS_NAME);
    new ApplicationContextRunner()
        .withUserConfiguration(configClass)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              Scheduler worker = Schedulers.newSingle("mdc-cleanup-test");
              try {
                Map<String, String> values =
                    Mono.just("start")
                        .publishOn(worker)
                        .map(
                            ignored ->
                                Map.of(
                                    "requestId", MDC.get("requestId"),
                                    "trustCode", String.valueOf(MDC.get("trustCode")),
                                    "userRole", String.valueOf(MDC.get("userRole"))))
                        .contextWrite(
                            Context.of(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, "req-456"))
                        .block();

                assertThat(values)
                    .containsEntry("requestId", "req-456")
                    .containsEntry("trustCode", "null")
                    .containsEntry("userRole", "null");
              } finally {
                worker.dispose();
              }
            });
  }
  @Test
  void mdcIsClearedWhenSameReactorWorkerThreadIsReusedForADifferentContext() throws Exception {
    Class<?> configClass = Class.forName(CONFIG_CLASS_NAME);
    new ApplicationContextRunner()
        .withUserConfiguration(configClass)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              Scheduler worker = Schedulers.newSingle("mdc-reuse-test");
              try {
                Map<String, String> firstRunValues =
                    Mono.just("start")
                        .publishOn(worker)
                        .map(
                            ignored ->
                                Map.of(
                                    "requestId", MDC.get("requestId"),
                                    "trustCode", MDC.get("trustCode"),
                                    "userRole", MDC.get("userRole")))
                        .contextWrite(
                            Context.of(
                                RequestCorrelation.REQUEST_ID_CONTEXT_KEY,
                                "req-first",
                                PortalIdentityCorrelation.TRUST_CODE_CONTEXT_KEY,
                                "JPM",
                                PortalIdentityCorrelation.USER_ROLE_CONTEXT_KEY,
                                "MEMBER"))
                        .block();

                assertThat(firstRunValues)
                    .containsEntry("requestId", "req-first")
                    .containsEntry("trustCode", "JPM")
                    .containsEntry("userRole", "MEMBER");

                // Reuse the SAME worker thread for a second, unrelated execution whose context
                // carries only a requestId. If the registry's reset lambdas (MDC.remove) did not
                // run correctly after the first execution's scope closed on this thread, the
                // trustCode/userRole values set above would leak into this second run.
                Map<String, String> secondRunValues =
                    Mono.just("start")
                        .publishOn(worker)
                        .map(
                            ignored ->
                                Map.of(
                                    "requestId", MDC.get("requestId"),
                                    "trustCode", String.valueOf(MDC.get("trustCode")),
                                    "userRole", String.valueOf(MDC.get("userRole"))))
                        .contextWrite(
                            Context.of(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, "req-second"))
                        .block();

                assertThat(secondRunValues)
                    .containsEntry("requestId", "req-second")
                    .containsEntry("trustCode", "null")
                    .containsEntry("userRole", "null");
              } finally {
                worker.dispose();
              }
            });
  }

}
