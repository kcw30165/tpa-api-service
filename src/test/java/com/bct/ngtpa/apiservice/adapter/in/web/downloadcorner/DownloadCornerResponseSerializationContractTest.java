package com.bct.ngtpa.apiservice.adapter.in.web.downloadcorner;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.downloadcorner.response.DownloadCornerCategoryResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.downloadcorner.response.DownloadCornerDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.downloadcorner.response.DownloadCornerItemResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.downloadcorner.response.DownloadCornerResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class DownloadCornerResponseSerializationContractTest {

  @Test
  void serializesOnlyTheApprovedBaseWrapperAndDownloadCornerFields()
      throws JsonProcessingException {
    var response =
        new DownloadCornerResultResponse(
            new DownloadCornerDataResponse(
                List.of(
                    new DownloadCornerCategoryResponse(
                        "Forms",
                        List.of(
                            new DownloadCornerItemResponse(
                                "Member form",
                                "https://example.test/member-form.pdf",
                                "member-form.pdf"))),
                    new DownloadCornerCategoryResponse("Empty", List.of()))));
    var wrapped = MutationResponse.success(ApiStatus.SUCCESS, response, List.of());

    ObjectMapper objectMapper = new ObjectMapper();
    assertThat(objectMapper.readTree(objectMapper.writeValueAsString(wrapped)))
        .isEqualTo(
            objectMapper.readTree(
                """
                {
                  "success": true,
                  "status": "SUCCESS",
                  "result": {
                    "data": {
                      "categories": [
                        {
                          "categoryName": "Forms",
                          "items": [
                            {
                              "itemName": "Member form",
                              "itemLink": "https://example.test/member-form.pdf",
                              "fileName": "member-form.pdf"
                            }
                          ]
                        },
                        {
                          "categoryName": "Empty",
                          "items": []
                        }
                      ]
                    }
                  },
                  "messages": [],
                  "errors": []
                }
                """));
  }
}
