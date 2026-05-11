package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.MemberContextPurpose;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusResult;
import com.bct.ngtpa.apiservice.application.port.in.UpdateNotificationsReadStatusUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimNotificationReadStatusPort;
import com.bct.ngtpa.apiservice.application.port.out.MemberContextPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.domain.model.MessageStatus;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.format.DateTimeFormatter;

@RequiredArgsConstructor
public class UpdateNotificationsReadStatusService implements UpdateNotificationsReadStatusUseCase {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ApimNotificationReadStatusPort apimNotificationReadStatusPort;
    private final MemberContextPort memberContextPort;
    private final ReferenceDatePort referenceDatePort;

    @Override
    public Mono<UpdateNotificationsReadStatusResult> execute(UpdateNotificationsReadStatusCommand command) {
        return memberContextPort.resolveMemberContext(MemberContextPurpose.NOTIFICATIONS)
                .zipWith(referenceDatePort.resolveReferenceDate())
                .flatMap(tuple -> {
                    var memberContext = tuple.getT1();
                    var referenceDate = tuple.getT2();

                    var enrichedCommand = new UpdateNotificationsReadStatusCommand(
                            command.env(),
                            command.mbrType(),
                            command.notificationIds(),
                            memberContext.policyNo(),
                            memberContext.certNo(),
                            memberContext.userId(),
                            referenceDate.format(DATE_FORMATTER),
                            MessageStatus.READ);
                    return apimNotificationReadStatusPort.updateReadStatus(enrichedCommand);
                });
    }
}