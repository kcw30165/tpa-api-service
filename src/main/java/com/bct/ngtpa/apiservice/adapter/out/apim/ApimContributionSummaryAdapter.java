package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetContributionSummaryApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetContributionSummaryContDtlItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetContributionSummaryDataItem;
import com.bct.ngtpa.apiservice.application.dto.FetchContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.port.out.ApimContributionSummaryPort;
import com.bct.ngtpa.apiservice.config.ApimProperties;
import com.bct.ngtpa.apiservice.domain.model.ContributionEntry;
import com.bct.ngtpa.apiservice.domain.model.ContributionLabels;
import com.bct.ngtpa.apiservice.domain.model.ContributionSource;
import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryDataset;
import com.bct.ngtpa.apiservice.exception.ApimException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class ApimContributionSummaryAdapter implements ApimContributionSummaryPort {

    private static final String API_NAME = "/ws/NGTPA/v1/TRPGetContSumy";

    private final ApimWebClientFacade apimWebClientFacade;
    private final ApimCertificateService apimCertificateService;
    private final ApimPayloadCryptoService apimPayloadCryptoService;
    private final ApimProperties apimProperties;

    @Override
    public Mono<ContributionSummaryDataset> fetchContributionSummary(FetchContributionSummaryCommand command) {
        if (!apimProperties.getEncryption().isEnabled()) {
            var request = apimPayloadCryptoService.encryptRequest(
                    API_NAME, toApimRequest(command), GetContributionSummaryApimRequest.class, null);
            return apimWebClientFacade.post(API_NAME, request)
                    .map(body -> apimPayloadCryptoService.decryptResponseEnvelope(
                            API_NAME, body, GetContributionSummaryDataItem.class, null))
                    .map(this::toContributionSummaryDataset);
        }

        return apimCertificateService.getBctPublicKey()
                .flatMap(publicKey -> {
                    var request = apimPayloadCryptoService.encryptRequest(
                            API_NAME, toApimRequest(command), GetContributionSummaryApimRequest.class, publicKey);
                    return apimWebClientFacade.post(API_NAME, request)
                            .map(body -> apimPayloadCryptoService.decryptResponseEnvelope(
                                    API_NAME, body, GetContributionSummaryDataItem.class, publicKey))
                            .map(this::toContributionSummaryDataset);
                });
    }

    private GetContributionSummaryApimRequest toApimRequest(FetchContributionSummaryCommand command) {
        return GetContributionSummaryApimRequest.builder()
                .policyNo(command.policyNo())
                .certNo(command.certNo())
                .userId(command.userId())
                .coverFrom(command.coverFrom())
                .coverTo(command.coverTo())
                .build();
    }

    private ContributionSummaryDataset toContributionSummaryDataset(ApimResponseEnvelope<GetContributionSummaryDataItem> response) {
        var payload = response != null ? response.getResponse() : null;
        if (payload == null) {
            throw new ApimException(HttpStatus.BAD_GATEWAY, "APIM response payload is missing.");
        }
        if (StringUtils.hasText(payload.getErrMessage())) {
            throw new ApimException(HttpStatus.BAD_GATEWAY, payload.getErrMessage());
        }
        if (CollectionUtils.isEmpty(payload.getData())) {
            return new ContributionSummaryDataset("", List.of(), List.of());
        }

        Map<String, ContributionSource> sources = new LinkedHashMap<>();
        String currency = payload.getData().stream()
                .filter(Objects::nonNull)
                .map(GetContributionSummaryDataItem::getCurrency)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
        List<ContributionEntry> entries = payload.getData().stream()
                .filter(Objects::nonNull)
                .flatMap(item -> {
                    var dispSources = item.getDispSrc() == null ? List.<com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetContributionSummaryDispSrcItem>of() : item.getDispSrc();
                    dispSources.stream()
                            .filter(Objects::nonNull)
                            .forEach(sourceItem -> sources.putIfAbsent(
                                    sourceItem.getDispSrc(),
                                    new ContributionSource(
                                            sourceItem.getDispSrc(),
                                            new ContributionLabels(sourceItem.getSrcDesc(), sourceItem.getSrcChinDesc()),
                                            sourceItem.getSeq())));
                    var details = item.getContDtl() == null ? List.<GetContributionSummaryContDtlItem>of() : item.getContDtl();
                    return details.stream();
                })
                .filter(Objects::nonNull)
                .map(this::toContributionEntry)
                .toList();

        return new ContributionSummaryDataset(currency, List.copyOf(sources.values()), entries);
    }

    private ContributionEntry toContributionEntry(GetContributionSummaryContDtlItem item) {
        return new ContributionEntry(
                item.getDispSrc(),
                item.getCoverFrom(),
                item.getCoverTo(),
                item.getDealDate(),
                item.getContAmt());
    }
}