package com.bct.ngtpa.apiservice.controller;

import com.bct.ngtpa.apiservice.dto.MessageBoardResponse;
import com.bct.ngtpa.apiservice.dto.apim.GetMessageBoardRequest;
import com.bct.ngtpa.apiservice.service.ApimClientService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/messages")
public class MessageBoardController {

    private final ApimClientService apimClientService;

    public MessageBoardController(ApimClientService apimClientService) {
        this.apimClientService = apimClientService;
    }

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<MessageBoardResponse> getMessageBoard(
            @Valid @RequestBody GetMessageBoardRequest getMessageBoardRequest) {
        return apimClientService.getMessageBoard(getMessageBoardRequest);
    }
}
