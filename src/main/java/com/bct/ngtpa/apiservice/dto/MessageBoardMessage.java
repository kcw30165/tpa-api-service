package com.bct.ngtpa.apiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageBoardMessage {
    private String msgCode;
    private String msgCodeLong;
    private Integer seq;
    private String msgCate;
    private String msgContentChi;
    private String msgContentEng;
    private String startDatetime;
    private String msgStatus;
}
