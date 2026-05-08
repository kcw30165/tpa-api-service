package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.GetNotificationsCommand;
import com.bct.ngtpa.apiservice.application.dto.MemberContextPurpose;
import com.bct.ngtpa.apiservice.application.dto.NotificationDateOptions;
import com.bct.ngtpa.apiservice.application.dto.NotificationListResult;
import com.bct.ngtpa.apiservice.application.port.in.GetNotificationsUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimNoticeMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.MemberContextPort;
import com.bct.ngtpa.apiservice.config.logging.LogExecution;
import com.bct.ngtpa.apiservice.domain.model.NoticeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GetNotificationsService implements GetNotificationsUseCase {

    // TODO: read refDate from config server once ReferenceDatePort is wired for notifications (Stage 1.2)
    private static final String HARDCODED_REF_DATE = "01/10/2025";

    private final ApimNoticeMessagePort apimNoticeMessagePort;
    private final MemberContextPort memberContextPort;

    @Override
        @LogExecution(value = "usecase.getNotifications", logArgs = true)
    public Mono<NotificationListResult> execute(GetNotificationsCommand command) {
        var dateOptions = NotificationDateOptions.resolve(command.dateFormat(), command.timezone());
        LocalDateTime now = dateOptions.now();

        return memberContextPort.resolveMemberContext(MemberContextPurpose.NOTIFICATIONS)
                .flatMap(memberContext -> {
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
                            HARDCODED_REF_DATE);

                    return apimNoticeMessagePort.fetchNotifications(enriched)
                            .map(result -> {
                                var visible = result.notifications().stream()
                                        .filter(message -> message.isVisible(now))
                                        .sorted(Comparator.comparingInt(
                                                m -> Optional.ofNullable(m.seq()).orElse(Integer.MAX_VALUE)))
                                        .toList();
                                return new NotificationListResult(visible, dateOptions);
                            });
                });
    }
}
