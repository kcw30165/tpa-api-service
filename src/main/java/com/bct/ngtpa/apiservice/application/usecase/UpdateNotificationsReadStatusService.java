package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.MemberContextPurpose;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusResult;
import com.bct.ngtpa.apiservice.application.port.in.UpdateNotificationsReadStatusUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimNotificationReadStatusPort;
import com.bct.ngtpa.apiservice.application.port.out.MemberContextPort;
import com.bct.ngtpa.apiservice.config.logging.LogExecution;
import com.bct.ngtpa.apiservice.domain.model.MessageStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UpdateNotificationsReadStatusService implements UpdateNotificationsReadStatusUseCase {

    // TODO: read ref-date from config server once ReferenceDatePort is wired for notifications (Stage 1.2)
    private static final String HARDCODED_REF_DATE = "01/10/2025";

    private final ApimNotificationReadStatusPort apimNotificationReadStatusPort;
    private final MemberContextPort memberContextPort;

    @Override
    @LogExecution(value = "usecase.updateNotificationsReadStatus", logArgs = true)
    public Mono<UpdateNotificationsReadStatusResult> execute(UpdateNotificationsReadStatusCommand command) {
        return memberContextPort.resolveMemberContext(MemberContextPurpose.NOTIFICATIONS)
                .flatMap(memberContext -> {
                    var enrichedCommand = new UpdateNotificationsReadStatusCommand(
                            command.env(),
                            command.mbrType(),
                            command.notificationIds(),
                            memberContext.policyNo(),
                            memberContext.certNo(),
                            memberContext.userId(),
                            HARDCODED_REF_DATE,
                            MessageStatus.READ);
                    return apimNotificationReadStatusPort.updateReadStatus(enrichedCommand);
                });
    }
}