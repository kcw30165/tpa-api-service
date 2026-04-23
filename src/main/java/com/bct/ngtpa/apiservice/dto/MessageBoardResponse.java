package com.bct.ngtpa.apiservice.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageBoardResponse {
    private Integer page;
    private Integer size;
    private List<MessageBoardMessage> messages;
}
