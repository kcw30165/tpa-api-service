package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.GetNotificationsCommand;
import com.bct.ngtpa.apiservice.application.dto.NotificationDateOptions;
import com.bct.ngtpa.apiservice.application.dto.NotificationListResult;
import com.bct.ngtpa.apiservice.application.port.in.GetNotificationsUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimNoticeMessagePort;
import com.bct.ngtpa.apiservice.domain.model.NoticeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GetNotificationsService implements GetNotificationsUseCase {

    private static final DateTimeFormatter REF_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // TODO: extract from access token once auth server is implemented
    private static final String HARDCODED_POLICY_NO = "00000000118";
    private static final String HARDCODED_CERT_NO   = "2";
    private static final String HARDCODED_USER_ID   = "C402400A";

    private final ApimNoticeMessagePort apimNoticeMessagePort;

    @Override
    public Mono<NotificationListResult> execute(GetNotificationsCommand command) {
        var dateOptions = NotificationDateOptions.resolve(command.dateFormat(), command.timezone());

        // TODO: read refDate from config server; fall back to today
        String refDate = LocalDate.now(dateOptions.zoneId()).format(REF_DATE_FORMATTER);

        var enriched = new GetNotificationsCommand(
                command.env(),
                command.mbrType(),
                command.page(),
                command.size(),
                command.dateFormat(),
                command.timezone(),
                HARDCODED_POLICY_NO,
                HARDCODED_CERT_NO,
                HARDCODED_USER_ID,
                refDate
        );

        LocalDateTime now = dateOptions.now();

        return apimNoticeMessagePort.fetchNotifications(enriched)
                .map(result -> {
                    var visible = result.notifications().stream()
                            .filter(message -> message.isVisible(now))
                            .sorted(Comparator.comparingInt(
                                    m -> Optional.ofNullable(m.seq()).orElse(Integer.MAX_VALUE)))
                            .toList();
                    return new NotificationListResult(visible, dateOptions);
                });
    }
}
