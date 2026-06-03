package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationFieldMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.MemberInfoResult;
import com.bct.ngtpa.apiservice.application.port.in.GetPersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimMemberInfoPort;
import com.bct.ngtpa.apiservice.application.port.out.PortalAccessContextPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

@RequiredArgsConstructor
public class GetPersonalInformationService implements GetPersonalInformationUseCase {

    private final ApimMemberInfoPort apimMemberInfoPort;
    private final PortalAccessContextPort portalAccessContextPort;
    private final PersonalInformationFieldMapper mapper;
    private final BffPagesProperties bffPagesProperties;

    @Override
    public Mono<Map<String, Object>> execute(GetPersonalInformationCommand command) {
        String accountRef = command.accountRef();

        return portalAccessContextPort.resolvePortalAccessContext(accountRef)
                .flatMap(ctx -> {
                    var apimCmd = new com.bct.ngtpa.apiservice.application.dto.FetchMemberInfoCommand(
                            ctx.account().accountEnv(),
                            ctx.account().policyNo(),
                            ctx.account().certNo(),
                            ctx.actor().actorUserId()
                    );

                    return apimMemberInfoPort.fetchMemberInfo(apimCmd)
                            .map(memberInfoResult -> mapToPage(memberInfoResult));
                });
    }

    private Map<String, Object> mapToPage(MemberInfoResult memberInfoResult) {
        Map<String, Object> payload = memberInfoResult.getPayload();

        Map<String, String> apimConfig = new LinkedHashMap<>();
        Object cfgObj = payload.get("config");
        if (cfgObj instanceof Map<?, ?> cfgMap) {
            for (Map.Entry<?, ?> e : cfgMap.entrySet()) {
                apimConfig.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
        }

        Map<String, Object> apimData = new LinkedHashMap<>();
        Object dataObj = payload.get("data");
        if (dataObj instanceof Map<?, ?> dataMap) {
            apimData.putAll((Map) dataMap);
        }

        // Pass the configured BffPagesProperties so mapper can resolve the page schema
        return mapper.map(apimData, apimConfig, bffPagesProperties);
    }
}
