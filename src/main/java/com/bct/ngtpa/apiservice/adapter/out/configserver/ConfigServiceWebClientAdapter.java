package com.bct.ngtpa.apiservice.adapter.out.configserver;

import com.bct.ngtpa.apiservice.adapter.out.configserver.config.ConfigServiceProperties;
import com.bct.ngtpa.apiservice.adapter.out.configserver.dto.ConfigServiceRequest;
import com.bct.ngtpa.apiservice.adapter.out.configserver.dto.ConfigServiceResponse;
import com.bct.ngtpa.apiservice.application.dto.ConfigEntry;
import com.bct.ngtpa.apiservice.application.dto.ConfigQuery;
import com.bct.ngtpa.apiservice.application.dto.ConfigUpsertCommand;
import com.bct.ngtpa.apiservice.application.port.out.ConfigServicePort;
import com.bct.ngtpa.apiservice.exception.ConfigServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigServiceWebClientAdapter implements ConfigServicePort {

    private static final String CONFIGS_PATH = "/api/configs";

    private final WebClient configServiceWebClient;
    private final ConfigServiceProperties configServiceProperties;

    @Override
    public Mono<List<ConfigEntry>> listConfigs(ConfigQuery query) {
        return configServiceWebClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(CONFIGS_PATH);
                    if (StringUtils.hasText(query.application())) {
                        uriBuilder.queryParam("application", query.application());
                    }
                    if (StringUtils.hasText(query.profile())) {
                        uriBuilder.queryParam("profile", query.profile());
                    }
                    if (StringUtils.hasText(query.label())) {
                        uriBuilder.queryParam("label", query.label());
                    }
                    if (StringUtils.hasText(query.configKey())) {
                        uriBuilder.queryParam("configKey", query.configKey());
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> {
                                    log.error("Config Service error: status={}", response.statusCode().value());
                                    return new ConfigServiceException(
                                            response.statusCode(),
                                            "Config Service returned error status: " + response.statusCode().value());
                                }))
                .bodyToFlux(ConfigServiceResponse.class)
                .collectList()
                .map(responses -> responses.stream()
                        .map(this::toConfigEntry)
                        .toList());
    }

    @Override
    public Mono<ConfigEntry> upsertConfig(ConfigUpsertCommand command) {
        ConfigServiceRequest request = new ConfigServiceRequest(
                command.application(),
                command.profile(),
                command.label(),
                command.configKey(),
                command.configValue());

        return configServiceWebClient.put()
                .uri(CONFIGS_PATH)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> {
                                    log.error("Config Service error: status={}", response.statusCode().value());
                                    return new ConfigServiceException(
                                            response.statusCode(),
                                            "Config Service returned error status: " + response.statusCode().value());
                                }))
                .bodyToMono(ConfigServiceResponse.class)
                .map(this::toConfigEntry);
    }

    @Override
    public Mono<Void> deleteConfig(String application, String profile, String label, String configKey) {
        if (!StringUtils.hasText(application) || !StringUtils.hasText(profile)
                || !StringUtils.hasText(label) || !StringUtils.hasText(configKey)) {
            return Mono.error(new IllegalArgumentException(
                    "application, profile, label, and configKey are all required for delete"));
        }
        return configServiceWebClient.delete()
                .uri("/api/configs/{application}/{profile}/{label}/{configKey}",
                        application, profile, label, configKey)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> {
                                    log.error("Config Service error: status={}", response.statusCode().value());
                                    return new ConfigServiceException(
                                            response.statusCode(),
                                            "Config Service returned error status: " + response.statusCode().value());
                                }))
                .toBodilessEntity()
                .then();
    }

    private ConfigEntry toConfigEntry(ConfigServiceResponse response) {
        return new ConfigEntry(
                response.getApplication(),
                response.getProfile(),
                response.getLabel(),
                response.getConfigKey(),
                response.getConfigValue());
    }
}
