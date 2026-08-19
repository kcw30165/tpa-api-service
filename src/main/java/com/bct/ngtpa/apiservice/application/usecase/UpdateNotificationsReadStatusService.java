package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusResult;
import com.bct.ngtpa.apiservice.application.port.in.UpdateNotificationsReadStatusUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimNotificationReadStatusPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.domain.model.MessageStatus;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class UpdateNotificationsReadStatusService implements UpdateNotificationsReadStatusUseCase {
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private final ApimNotificationReadStatusPort apimNotificationReadStatusPort;
  private final ReferenceDatePort referenceDatePort;

  @Override
  public Mono<UpdateNotificationsReadStatusResult> execute(
      UpdateNotificationsReadStatusCommand command) {
    return referenceDatePort
        .resolveReferenceDate(command.accountEnv())
        .flatMap(
            refDate ->
                apimNotificationReadStatusPort.updateReadStatus(
                    new UpdateNotificationsReadStatusCommand(
                        command.accountEnv(),
                        command.mbrType(),
                        command.notificationIds(),
                        command.policyNo(),
                        command.certNo(),
                        command.userId(),
                        refDate.format(DATE_FORMATTER),
                        MessageStatus.READ)));
  }
}
