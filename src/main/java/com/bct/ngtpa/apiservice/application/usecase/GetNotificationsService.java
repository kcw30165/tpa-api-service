package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.GetNotificationsCommand;
import com.bct.ngtpa.apiservice.application.dto.NotificationDateOptions;
import com.bct.ngtpa.apiservice.application.dto.NotificationListResult;
import com.bct.ngtpa.apiservice.application.port.in.GetNotificationsUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimNoticeMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class GetNotificationsService implements GetNotificationsUseCase {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ApimNoticeMessagePort apimNoticeMessagePort;
    private final CurrentPortalAccessContextResolver currentPortalAccessContextResolver;
    private final ReferenceDatePort referenceDatePort;

    @Override
    public Mono<NotificationListResult> execute(GetNotificationsCommand command) {
        var dateOptions = NotificationDateOptions.resolve(command.dateFormat(), command.timezone());
        LocalDateTime now = dateOptions.now();

        return currentPortalAccessContextResolver.current()
                .zipWith(referenceDatePort.resolveReferenceDate())
                .flatMap(tuple -> {
                    var ctx = tuple.getT1();
                    var referenceDate = tuple.getT2();

                    var enriched = new GetNotificationsCommand(
                            ctx.account().accountEnv(),
                            "",
                            command.page(),
                            command.size(),
                            command.dateFormat(),
                            command.timezone(),
                            ctx.account().policyNo(),
                            ctx.account().certNo(),
                            ctx.actor().actorUserId(),
                            referenceDate.format(DATE_FORMATTER));

                                        return apimNoticeMessagePort.fetchNotifications(enriched)
                                                        .map(result -> {
                                                                var visible = result.notifications().stream()
                                                                                .filter(message -> message
                                                                                                .isVisible(now))
                                                                                .sorted(Comparator.comparingInt(
                                                                                                m -> Optional.ofNullable(
                                                                                                                m.seq())
                                                                                                                .orElse(Integer.MAX_VALUE)))
                                                                                .toList();
                                                                return new NotificationListResult(visible, dateOptions);
                                                        });
                                });
        }
}
