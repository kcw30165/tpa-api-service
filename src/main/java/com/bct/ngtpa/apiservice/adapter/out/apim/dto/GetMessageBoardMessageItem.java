package com.bct.ngtpa.apiservice.adapter.out.apim.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetMessageBoardMessageItem {

    @JsonProperty("msg-code")
    private String msgCode;

    @JsonProperty("msg-code-long")
    private String msgCodeLong;

    @JsonProperty("seq")
    private Integer seq;

    @JsonProperty("msg-cate")
    private String msgCate;

    @JsonProperty("msg-title")
    private String msgTitle;

    @JsonProperty("msg-content-chi")
    private String msgContentChi;

    @JsonProperty("msg-content-eng")
    private String msgContentEng;

    @JsonProperty("start-datetime")
    private String startDatetime;

    @JsonProperty("msg-status")
    private String msgStatus;

    @JsonProperty("is-read")
    private boolean isRead;
}
