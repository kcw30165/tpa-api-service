package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.NotificationReadStatusWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.NotificationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.notification.NotificationController;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.NotificationListResult;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.domain.model.AudienceType;
import com.bct.ngtpa.apiservice.domain.model.Hyperlink;
import com.bct.ngtpa.apiservice.domain.model.MessageStatus;
import com.bct.ngtpa.apiservice.domain.model.NoticeMessage;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class NotificationGetLanguageHttpContractTest {

  private static final String CONTENT_PATH = "$.result.data.notifications[0].msgContent";
  private static final String CHINESE = "Chi content";
  private static final String ENGLISH = "Eng content";

  @Test
  void zhHkReturnsChineseContentInSingleMsgContentField() {
    get("zh_HK", CHINESE, ENGLISH).jsonPath(CONTENT_PATH).isEqualTo(CHINESE);
  }

  @Test
  void zhHkFallsBackToEnglishWhenChineseContentIsBlank() {
    get("zh_HK", " ", ENGLISH).jsonPath(CONTENT_PATH).isEqualTo(ENGLISH);
  }

  @Test
  void zhHkFallsBackToEnglishWhenChineseContentIsNull() {
    get("zh_HK", null, ENGLISH).jsonPath(CONTENT_PATH).isEqualTo(ENGLISH);
  }

  @Test
  void englishLanguageReturnsEnglishContent() {
    get("en", CHINESE, ENGLISH).jsonPath(CONTENT_PATH).isEqualTo(ENGLISH);
  }

  @Test
  void missingAcceptLanguageReturnsEnglishContent() {
    get(null, CHINESE, ENGLISH).jsonPath(CONTENT_PATH).isEqualTo(ENGLISH);
  }

  @Test
  void responseDoesNotContainBilingualContentFields() {
    get("zh-HK", CHINESE, ENGLISH)
        .jsonPath(CONTENT_PATH)
        .isEqualTo(CHINESE)
        .jsonPath("$.result.data.notifications[0].msgContentChi")
        .doesNotExist()
        .jsonPath("$.result.data.notifications[0].msgContentEng")
        .doesNotExist();
  }

  @Test
  void responseCarriesCategoryCodeAndNoJavaAuthoredTitle() {
    get("en", CHINESE, ENGLISH)
        .jsonPath("$.result.data.notifications[0].category")
        .isEqualTo("GENERAL")
        .jsonPath("$.result.data.notifications[0].msgTitle")
        .doesNotExist();
  }

  private static WebTestClient.BodyContentSpec get(
      String language, String chinese, String english) {
    var controller =
        new NotificationController(
            command -> Mono.just(new NotificationListResult(List.of(message(chinese, english)))),
            command -> Mono.error(new UnsupportedOperationException()),
            new NotificationWebMapper(),
            new NotificationReadStatusWebMapper(),
            resolver());
    WebTestClient.RequestHeadersSpec<?> request =
        WebTestClient.bindToController(controller).build().get().uri("/api/v1/notifications");
    if (language != null) {
      request.header("Accept-Language", language);
    }
    return request.exchange().expectStatus().isOk().expectBody();
  }

  private static NoticeMessage message(String chinese, String english) {
    return new NoticeMessage(
        "MSG-1",
        "MSG-1-LONG",
        1,
        "GENERAL",
        chinese,
        english,
        false,
        LocalDateTime.of(2026, 5, 4, 8, 30),
        null,
        MessageStatus.UNREAD,
        (AudienceType) null,
        null,
        List.<Hyperlink>of());
  }

  private static CurrentPortalAccessContextResolver resolver() {
    PortalAccessContext context =
        new PortalAccessContext(
            new ActorContext("USER-1", ActorUserRole.ROLE_USER_MBR),
            new AccountContext(
                "ACC-1", "JP", "POL-1", "CERT-1", "TRUST", "SCHEME", TermStatus.BLANK, null));
    return new CurrentPortalAccessContextResolver() {
      public Mono<PortalAccessContext> current() {
        return Mono.just(context);
      }

      public Mono<PortalAccessContext> currentOrEmpty() {
        return Mono.just(context);
      }
    };
  }
}
