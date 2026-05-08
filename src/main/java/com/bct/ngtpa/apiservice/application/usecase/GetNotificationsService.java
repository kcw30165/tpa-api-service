package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.GetNotificationsCommand;
import com.bct.ngtpa.apiservice.application.dto.MemberContextPurpose;
import com.bct.ngtpa.apiservice.application.dto.NotificationDateOptions;
import com.bct.ngtpa.apiservice.application.dto.NotificationListResult;
import com.bct.ngtpa.apiservice.application.port.in.GetNotificationsUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimNoticeMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.MemberContextPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.config.logging.LogExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GetNotificationsService implements GetNotificationsUseCase {

        private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        private final ApimNoticeMessagePort apimNoticeMessagePort;
        private final MemberContextPort memberContextPort;
        private final ReferenceDatePort referenceDatePort;

        @Override
        @LogExecution(value = "usecase.getNotifications", logArgs = true)
        public Mono<NotificationListResult> execute(GetNotificationsCommand command) {
                var dateOptions = NotificationDateOptions.resolve(command.dateFormat(), command.timezone());
                LocalDateTime now = dateOptions.now();

                return memberContextPort.resolveMemberContext(MemberContextPurpose.NOTIFICATIONS)
                                .zipWith(referenceDatePort.resolveReferenceDate())
                                .flatMap(tuple -> {
                                        var memberContext = tuple.getT1();
                                        var referenceDate = tuple.getT2();

                                        var enriched = new GetNotificationsCommand(
                                                        command.env(),
                                                        command.mbrType(),
                                                        command.page(),
                                                        command.size(),
                                                        command.dateFormat(),
                                                        command.timezone(),
                                                        memberContext.policyNo(),
                                                        memberContext.certNo(),
                                                        memberContext.userId(),
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
