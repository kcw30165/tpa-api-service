package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusResult;
import com.bct.ngtpa.apiservice.application.port.in.UpdateNotificationsReadStatusUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimNotificationReadStatusPort;
import com.bct.ngtpa.apiservice.domain.model.MessageStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UpdateNotificationsReadStatusService implements UpdateNotificationsReadStatusUseCase {

    // TODO: extract cert-no from access token once auth server is implemented.
    private static final String HARDCODED_CERT_NO = "2";
    // TODO: extract policy-no from access token once auth server is implemented.
    private static final String HARDCODED_POLICY_NO = "00000000118";
    // TODO: extract user-id from access token once auth server is implemented.
    private static final String HARDCODED_USER_ID = "C402400A";
    // TODO: read ref-date from config server once it is implemented.
    private static final String HARDCODED_REF_DATE = "01/10/2025";

    private final ApimNotificationReadStatusPort apimNotificationReadStatusPort;

    @Override
    public Mono<UpdateNotificationsReadStatusResult> execute(UpdateNotificationsReadStatusCommand command) {
        var enrichedCommand = new UpdateNotificationsReadStatusCommand(
                command.env(),
                command.mbrType(),
                command.notificationIds(),
                HARDCODED_POLICY_NO,
                HARDCODED_CERT_NO,
                HARDCODED_USER_ID,
                HARDCODED_REF_DATE,
                MessageStatus.READ);

        return apimNotificationReadStatusPort.updateReadStatus(enrichedCommand);
    }
}