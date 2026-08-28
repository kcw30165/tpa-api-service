package com.bct.ngtpa.apiservice.adapter.out.apim.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.adapter.out.apim.metadata.dto.GeneralMetadataApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.metadata.dto.GeneralMetadataApimRow;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.metadata.*;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class ApimGeneralMetadataSourceAdapterTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test void oneKeyProducesOneTargetedAuthenticatedSafeExchange() throws Exception {
    var exchanges = new CopyOnWriteArrayList<ApimExchangeRequest<?, ?>>();
    var adapter = new ApimGeneralMetadataSourceAdapter(capturing(exchanges, false));
    var result = adapter.fetch(command(Set.of(key(GeneralMetadataName.FUND_PRICE)))).block();
    assertThat(exchanges).singleElement().satisfies(exchange -> {
      assertThat(exchange.path()).isEqualTo("/ws/NGTPA/v1/TRPGetGeneralMetaData");
      assertThat(exchange.requestType()).isEqualTo(GeneralMetadataApimRequest.class);
      assertThat(exchange.responseDataType()).isEqualTo(GeneralMetadataApimRow.class);
      assertThat(exchange.databaseScope()).isEqualTo(ApimDatabaseScope.ACCOUNT_ENV);
      assertThat(exchange.retryEligibility()).isEqualTo(ApimRetryEligibility.SAFE_BUSINESS_CALL);
      assertThat(exchange.credentialContext().actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
      assertThat(exchange.credentialContext().accountEnv()).isEqualTo("DB");
      assertThat(exchange.request()).isEqualTo(GeneralMetadataApimRequest.targeted("DB", "DBA", "00000008802", key(GeneralMetadataName.FUND_PRICE)));
    });
    assertThat(result.entries()).singleElement();
  }

  @Test void multipleKeysAreTargetedInDeterministicOrderAndCombined() {
    var exchanges = new CopyOnWriteArrayList<ApimExchangeRequest<?, ?>>();
    var adapter = new ApimGeneralMetadataSourceAdapter(capturing(exchanges, false));
    var result = adapter.fetch(command(Set.of(key(GeneralMetadataName.FX_RATE), key(GeneralMetadataName.FUND_PRICE)))).block();
    assertThat(exchanges).hasSize(2);
    assertThat(exchanges)
        .allSatisfy(exchange -> {
          assertThat(exchange.path()).isEqualTo("/ws/NGTPA/v1/TRPGetGeneralMetaData");
          assertThat(exchange.requestType()).isEqualTo(GeneralMetadataApimRequest.class);
          assertThat(exchange.responseDataType()).isEqualTo(GeneralMetadataApimRow.class);
          assertThat(exchange.databaseScope()).isEqualTo(ApimDatabaseScope.ACCOUNT_ENV);
          assertThat(exchange.retryEligibility()).isEqualTo(ApimRetryEligibility.SAFE_BUSINESS_CALL);
          assertThat(exchange.credentialContext().actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
          assertThat(exchange.credentialContext().accountEnv()).isEqualTo("DB");
        });
    assertThat(exchanges).extracting(e -> ((GeneralMetadataApimRequest)e.request()).metadataName()).containsExactly(GeneralMetadataWireValues.nameToWire(GeneralMetadataName.FUND_PRICE), GeneralMetadataWireValues.nameToWire(GeneralMetadataName.FX_RATE));
    assertThat(result.entries()).hasSize(2);
  }

  @Test void emptyKeysUseOneExplicitUnfilteredRequest() {
    var exchanges = new CopyOnWriteArrayList<ApimExchangeRequest<?, ?>>();
    new ApimGeneralMetadataSourceAdapter(capturing(exchanges, false)).fetch(command(Set.of())).block();
    assertThat(exchanges).singleElement().extracting(ApimExchangeRequest::request)
        .isEqualTo(GeneralMetadataApimRequest.unfiltered("DB", "DBA", "00000008802"));
  }

  @Test void conflictingSourcesAndTechnicalFailuresArePreserved() {
    var exchanges = new CopyOnWriteArrayList<ApimExchangeRequest<?, ?>>();
    assertThatThrownBy(() -> new ApimGeneralMetadataSourceAdapter(capturing(exchanges, true))
        .fetch(command(Set.of(key(GeneralMetadataName.FX_RATE), key(GeneralMetadataName.FUND_PRICE)))).block())
        .hasRootCauseInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Conflicting general metadata resolution sources");
    var failure = new IllegalStateException("technical");
    assertThatThrownBy(() -> new ApimGeneralMetadataSourceAdapter(failing(failure))
        .fetch(command(Set.of(key(GeneralMetadataName.FUND_PRICE)))).block()).hasRootCause(failure);
  }

  @Test void malformedOperationContentIsPreserved() {
    assertThatThrownBy(() -> new ApimGeneralMetadataSourceAdapter(malformedContent())
        .fetch(command(Set.of(key(GeneralMetadataName.FUND_PRICE)))).block())
        .hasRootCauseInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unexpected general metadata policy");
  }

  @Test void conflictingValuesForTheSameResolvedKeyFail() {
    assertThatThrownBy(() -> new ApimGeneralMetadataSourceAdapter(conflictingValues())
        .fetch(command(Set.of(
            key(GeneralMetadataName.FUND_PRICE),
            key(GeneralMetadataName.FX_RATE)))).block())
        .hasRootCauseInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Conflicting resolved general metadata entry");
  }

  private FetchGeneralMetadataCommand command(Set<GeneralMetadataKey> keys) {
    return new FetchGeneralMetadataCommand(new GeneralMetadataScope("DB", "DBA", "00000008802"), keys, ActorUserRole.ROLE_USER_MBR);
  }
  private GeneralMetadataKey key(GeneralMetadataName name) { return new GeneralMetadataKey(name, GeneralMetadataType.PRECISION); }

  private ApimExchangeExecutor capturing(List<ApimExchangeRequest<?, ?>> exchanges, boolean conflictingSources) {
    return new ApimExchangeExecutor() {
      @Override @SuppressWarnings("unchecked")
      public <R,D> Mono<ApimResponseEnvelope<D>> postEnvelope(ApimExchangeRequest<R,D> exchange) {
        exchanges.add(exchange);
        var request=(GeneralMetadataApimRequest)exchange.request();
        String policy=conflictingSources && exchanges.size()==2 ? "00000008802" : "all";
        String json="{\"response\":{\"err-message\":\"\",\"data\":[{\"policy-no\":\""+policy+"\",\"metadata-name\":\""+request.metadataName()+"\",\"metadata-type\":\"precision\",\"metadata-value\":\"6\"}]}}";
        try {
          JavaType type=mapper.getTypeFactory().constructParametricType(ApimResponseEnvelope.class, GeneralMetadataApimRow.class);
          return Mono.just((ApimResponseEnvelope<D>)mapper.readValue(json,type));
        } catch(Exception exception) { return Mono.error(exception); }
      }
    };
  }
  private ApimExchangeExecutor malformedContent() {
    return responseExecutor((request, call) -> rowJson("foreign", request.metadataName(), "6"));
  }

  private ApimExchangeExecutor conflictingValues() {
    return responseExecutor((request, call) -> rowJson("all", "fund-pri", call == 1 ? "6" : "7"));
  }

  private ApimExchangeExecutor responseExecutor(
      java.util.function.BiFunction<GeneralMetadataApimRequest, Integer, String> rowFactory) {
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    return new ApimExchangeExecutor() {
      @Override @SuppressWarnings("unchecked")
      public <R,D> Mono<ApimResponseEnvelope<D>> postEnvelope(ApimExchangeRequest<R,D> exchange) {
        var request = (GeneralMetadataApimRequest) exchange.request();
        String json = "{\"response\":{\"err-message\":\"\",\"data\":["
            + rowFactory.apply(request, calls.incrementAndGet()) + "]}}";
        try {
          JavaType type = mapper.getTypeFactory()
              .constructParametricType(ApimResponseEnvelope.class, GeneralMetadataApimRow.class);
          return Mono.just((ApimResponseEnvelope<D>) mapper.readValue(json, type));
        } catch (Exception exception) {
          return Mono.error(exception);
        }
      }
    };
  }

  private String rowJson(String policyNo, String metadataName, String value) {
    return "{\"policy-no\":\"" + policyNo
        + "\",\"metadata-name\":\"" + metadataName
        + "\",\"metadata-type\":\"precision\",\"metadata-value\":\""
        + value + "\"}";
  }

  private static ApimExchangeExecutor failing(RuntimeException failure) {
    return new ApimExchangeExecutor() {
      @Override public <R,D> Mono<ApimResponseEnvelope<D>> postEnvelope(ApimExchangeRequest<R,D> request) { return Mono.error(failure); }
    };
  }
}
