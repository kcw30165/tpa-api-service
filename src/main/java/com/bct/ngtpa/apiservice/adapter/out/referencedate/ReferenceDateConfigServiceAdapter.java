package com.bct.ngtpa.apiservice.adapter.out.referencedate;

import com.bct.ngtpa.apiservice.adapter.out.configserver.ReferenceDateProperties;
import com.bct.ngtpa.apiservice.application.dto.ConfigUpsertCommand;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDateConfigUpsertCommand;
import com.bct.ngtpa.apiservice.application.port.out.ConfigServicePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDateConfigPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ReferenceDateConfigServiceAdapter implements ReferenceDateConfigPort {

    private final ConfigServicePort configServicePort;
    private final ReferenceDateProperties referenceDateProperties;

    @Override
    public Mono<Void> upsertReferenceDate(ReferenceDateConfigUpsertCommand command) {
        var configService = referenceDateProperties.getRefresh().getConfigService();
        return configServicePort.upsertConfig(new ConfigUpsertCommand(
                        requireText(configService.getApplication(), "reference-date.refresh.config-service.application"),
                        requireText(configService.getProfile(), "reference-date.refresh.config-service.profile"),
                        requireText(configService.getLabel(), "reference-date.refresh.config-service.label"),
                        command.configKey(),
                        command.configValue()))
                .then();
    }

    private String requireText(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(propertyName + " must be configured");
        }
        return value;
    }
}