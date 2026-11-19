package com.bct.ngtpa.apiservice.application.dto.downloadcorner;

public record DownloadCornerItem(
    int itemSeq,
    String itemNameEng,
    String itemNameChi,
    String itemLinkEng,
    String itemLinkChi,
    String fileNameEng,
    String fileNameChi) {}
