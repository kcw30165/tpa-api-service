package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.FetchContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.port.out.ApimContributionSummaryPort;
import com.bct.ngtpa.apiservice.domain.model.*;
import com.bct.ngtpa.apiservice.shared.logging.LogExecution;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ApimContributionSummaryAdapter implements ApimContributionSummaryPort {
 private static final String API_NAME="/ws/NGTPA/v1/TRPGetContSumy";
 private final ApimExchangeExecutor executor;
 @Override @LogExecution(value="apim.fetchContributionSummary",logArgs=true)
 public Mono<ContributionSummaryDataset> fetchContributionSummary(FetchContributionSummaryCommand command){
  var exchange=new ApimExchangeRequest<>(API_NAME,toApimRequest(command),GetContributionSummaryApimRequest.class,
   GetContributionSummaryDataItem.class,ApimDatabaseScope.ACCOUNT_ENV,
   new ApimCredentialResolutionContext(command.actorUserRole(),command.accountEnv()),ApimRetryEligibility.SAFE_BUSINESS_CALL);
  return executor.postEnvelope(exchange).map(this::toContributionSummaryDataset);
 }
 private GetContributionSummaryApimRequest toApimRequest(FetchContributionSummaryCommand command){return GetContributionSummaryApimRequest.builder().policyNo(command.policyNo()).certNo(command.certNo()).userId(command.userId()).coverFrom(command.coverFrom()).coverTo(command.coverTo()).build();}
 private ContributionSummaryDataset toContributionSummaryDataset(ApimResponseEnvelope<GetContributionSummaryDataItem> response){
  List<GetContributionSummaryDataItem> data=ApimResponseValidator.requireSuccessData(response); if(data.isEmpty())return new ContributionSummaryDataset("",List.of(),List.of());
  Map<String,ContributionSource> sources=new LinkedHashMap<>();
  String currency=data.stream().filter(Objects::nonNull).map(GetContributionSummaryDataItem::getCurrency).filter(StringUtils::hasText).findFirst().orElse("");
  List<ContributionEntry> entries=data.stream().filter(Objects::nonNull).flatMap(item->{var ds=item.getDispSrc()==null?List.<GetContributionSummaryDispSrcItem>of():item.getDispSrc(); ds.stream().filter(Objects::nonNull).forEach(x->sources.putIfAbsent(x.getDispSrc(),new ContributionSource(x.getDispSrc(),new ContributionLabels(x.getSrcDesc(),x.getSrcChinDesc()),x.getSeq()))); return (item.getContDtl()==null?List.<GetContributionSummaryContDtlItem>of():item.getContDtl()).stream();}).filter(Objects::nonNull).map(this::toEntry).toList();
  return new ContributionSummaryDataset(currency,List.copyOf(sources.values()),entries);
 }
 private ContributionEntry toEntry(GetContributionSummaryContDtlItem x){return new ContributionEntry(x.getDispSrc(),x.getCoverFrom(),x.getCoverTo(),x.getDealDate(),x.getContAmt());}
}
