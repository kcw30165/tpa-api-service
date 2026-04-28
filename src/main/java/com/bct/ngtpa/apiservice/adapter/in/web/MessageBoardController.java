package com.bct.ngtpa.apiservice.adapter.in.web;

import com.bct.ngtpa.apiservice.adapter.in.web.request.GetMessageBoardWebRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MessageBoardWebResponse;
import com.bct.ngtpa.apiservice.application.dto.GetMessageBoardCommand;
import com.bct.ngtpa.apiservice.application.port.in.GetMessageBoardUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageBoardController {

    private final GetMessageBoardUseCase getMessageBoardUseCase;

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<MessageBoardWebResponse> getMessageBoard(
            @Valid @RequestBody GetMessageBoardWebRequest request) {
        return getMessageBoardUseCase
                .execute(GetMessageBoardCommand.from(request))
                .map(MessageBoardWebResponse::from);
    }
}
