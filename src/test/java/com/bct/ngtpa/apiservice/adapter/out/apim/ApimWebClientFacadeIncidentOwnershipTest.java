package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.oauth.ApimTokenService;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.masking.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ApimWebClientFacadeIncidentOwnershipTest {
  private static final ApimCredentialResolutionContext CONTEXT =
      new ApimCredentialResolutionContext(ActorUserRole.ROLE_USER_MBR,"SECRET-ACCOUNT-ENV");
  private static final ApimCredentialProfile PROFILE =
      new ApimCredentialProfile("PRIVATE-PROFILE","cid","secret","key",null,"http://localhost",List.of(),null);
  private Logger logger; private ListAppender<ILoggingEvent> appender;
  @BeforeEach void attach(){logger=(Logger)LoggerFactory.getLogger(ApimWebClientFacade.class);appender=new ListAppender<>();appender.start();logger.addAppender(appender); }
  @AfterEach void detach(){logger.detachAppender(appender);appender.stop(); }

  @Test void explicitPlainAndEncryptedEntriesRejectNullOwnership() {
    var facade=facade(req->Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build()),(s,c)->Mono.just(PROFILE),new ObjectMapper());
    assertThatThrownBy(()->facade.postSuccessfulExchange("/p",Map.of(),ApimDatabaseScope.NON_ACCOUNT,CONTEXT,ApimRetryEligibility.NON_RETRYABLE,ApimResubmissionPolicy.NEVER,null)).isInstanceOf(NullPointerException.class).hasMessage("incidentOwnership must not be null");
    assertThatThrownBy(()->facade.postEncryptedSuccessfulExchange("/p",(p,k)->"cipher",ApimDatabaseScope.NON_ACCOUNT,CONTEXT,ApimRetryEligibility.NON_RETRYABLE,ApimResubmissionPolicy.NEVER,null)).isInstanceOf(NullPointerException.class).hasMessage("incidentOwnership must not be null");
  }

  @Test void httpFailureIsErrorForLegacyAndWarnForTerminalWithoutPrivateValues() {
    ExchangeFunction x=req->Mono.just(ClientResponse.create(HttpStatus.BAD_GATEWAY).body("PRIVATE-BODY").build());
    var facade=facade(x,(s,c)->Mono.just(PROFILE),new ObjectMapper());
    StepVerifier.create(facade.postSuccessfulExchange("/p",Map.of(),ApimDatabaseScope.NON_ACCOUNT,CONTEXT,ApimRetryEligibility.NON_RETRYABLE,ApimResubmissionPolicy.NEVER,ApimIncidentOwnership.APIM_OBSERVER)).expectError().verify();
    assertThat(appender.list).anySatisfy(e->assertThat(e.getLevel()).isEqualTo(Level.ERROR));
    appender.list.clear();
    StepVerifier.create(facade.postSuccessfulExchange("/p",Map.of(),ApimDatabaseScope.NON_ACCOUNT,CONTEXT,ApimRetryEligibility.NON_RETRYABLE,ApimResubmissionPolicy.NEVER,ApimIncidentOwnership.TERMINAL_CALLER)).expectError().verify();
    assertTerminalDiagnostics(1,"HTTP_ERROR");
  }

  @Test void emptyAndFailedProfileAreDiagnosticForTerminalAndLegacyRemainsError() {
    var empty=facade(req->Mono.empty(),(s,c)->Mono.empty(),new ObjectMapper());
    StepVerifier.create(empty.resolveProfile(ApimDatabaseScope.ACCOUNT_ENV,CONTEXT,ApimIncidentOwnership.TERMINAL_CALLER)).expectError().verify();
    assertTerminalDiagnostics(2,"PROFILE_RESOLUTION_FAILURE");
    appender.list.clear();
    var failed=facade(req->Mono.empty(),(s,c)->Mono.error(new IllegalStateException("PRIVATE-REASON")),new ObjectMapper());
    StepVerifier.create(failed.resolveProfile(ApimDatabaseScope.ACCOUNT_ENV,CONTEXT,ApimIncidentOwnership.TERMINAL_CALLER)).expectError().verify();
    assertTerminalDiagnostics(1,"PROFILE_RESOLUTION_FAILURE");
    appender.list.clear();
    StepVerifier.create(empty.resolveProfile(ApimDatabaseScope.ACCOUNT_ENV,CONTEXT,ApimIncidentOwnership.APIM_OBSERVER)).expectError().verify();
    assertThat(appender.list).allSatisfy(e->assertThat(e.getLevel()).isEqualTo(Level.ERROR));
  }

  @Test void serializerFailureIsWarnForTerminalWithoutThrowableProxyOrPrivateValues() {
    ObjectMapper mapper=mock(ObjectMapper.class);
    var sanitizer=mock(LoggingSanitizer.class);
    when(sanitizer.toSafeString(org.mockito.ArgumentMatchers.any())).thenThrow(new IllegalStateException("PRIVATE-SERIALIZER"));
    var facade=facade(req->Mono.empty(),(s,c)->Mono.empty(),mapper,sanitizer);
    StepVerifier.create(facade.resolveProfile(ApimDatabaseScope.ACCOUNT_ENV,CONTEXT,ApimIncidentOwnership.TERMINAL_CALLER)).expectError().verify();
    assertTerminalDiagnostics(2,"serializationFailure=true");
  }

  private void assertTerminalDiagnostics(int size,String marker){assertThat(appender.list).hasSize(size).allSatisfy(e->{assertThat(e.getLevel()).isEqualTo(Level.WARN);assertThat(e.getFormattedMessage()).contains(marker).doesNotContain("PRIVATE-BODY","PRIVATE-REASON","PRIVATE-SERIALIZER","SECRET-ACCOUNT-ENV","PRIVATE-PROFILE","credential","ciphertext");assertThat(e.getThrowableProxy()).isNull(); }); }
  private ApimWebClientFacade facade(ExchangeFunction x,ApimCredentialProfileResolver r,ObjectMapper m){return facade(x,r,m,new LoggingSanitizer(m,new MaskingService(new MaskingProperties()))); }
  private ApimWebClientFacade facade(ExchangeFunction x,ApimCredentialProfileResolver r,ObjectMapper m,LoggingSanitizer s){var b=WebClient.builder().exchangeFunction(x);var props=new ApimProperties();var tokens=new ApimTokenService(b,props){@Override public Mono<String> getAccessToken(ApimCredentialProfile p){return Mono.just("token"); }};return new ApimWebClientFacade(WebClient.create(),b,m,r,tokens,new ApimCertificateService(b,props,null),props,null,s,new com.bct.ngtpa.apiservice.adapter.out.apim.client.ApimContextPropagationExchangeFilter(),new com.bct.ngtpa.apiservice.adapter.out.apim.client.ApimRequestLoggingExchangeFilter(disabled(),props,m)); }
  private static com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingProperties disabled(){var p=new com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingProperties();p.setEnabled(false);p.setLogHeaders(false);p.setHeaderAllowlist(List.of());return p; }
}
