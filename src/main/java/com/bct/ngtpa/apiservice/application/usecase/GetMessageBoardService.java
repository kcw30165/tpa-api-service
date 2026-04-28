package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.GetMessageBoardCommand;
import com.bct.ngtpa.apiservice.application.dto.MessageBoardResult;
import com.bct.ngtpa.apiservice.application.port.in.GetMessageBoardUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimNoticeMessagePort;
import com.bct.ngtpa.apiservice.domain.model.NoticeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GetMessageBoardService implements GetMessageBoardUseCase {

    private final ApimNoticeMessagePort apimNoticeMessagePort;

    @Override
    public Mono<MessageBoardResult> execute(GetMessageBoardCommand command) {
        return apimNoticeMessagePort.fetchMessages(command)
                .map(result -> {
                    var visible = result.messages().stream()
                            .filter(NoticeMessage::isVisible)
                            .sorted(Comparator.comparingInt(
                                    m -> Optional.ofNullable(m.seq()).orElse(Integer.MAX_VALUE)))
                            .toList();
                    return new MessageBoardResult(result.page(), result.size(), visible);
                });
    }
}
