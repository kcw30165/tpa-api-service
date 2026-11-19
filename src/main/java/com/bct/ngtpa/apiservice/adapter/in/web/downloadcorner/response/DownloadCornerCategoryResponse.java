package com.bct.ngtpa.apiservice.adapter.in.web.downloadcorner.response;

import java.util.List;

public record DownloadCornerCategoryResponse(
    String categoryName, List<DownloadCornerItemResponse> items) {}
