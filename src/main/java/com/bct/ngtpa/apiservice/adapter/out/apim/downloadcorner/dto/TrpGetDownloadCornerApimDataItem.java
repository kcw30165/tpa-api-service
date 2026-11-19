package com.bct.ngtpa.apiservice.adapter.out.apim.downloadcorner.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TrpGetDownloadCornerApimDataItem(
    @JsonProperty(CATEGORIES_KEY) ApimField<List<Category>> categories) {

  public static final String DATA_KEY = "response.data";
  public static final String CATEGORIES_KEY = "categories";
  public static final String CATEGORY_SEQ_KEY = "category-seq";
  public static final String CATEGORY_NAME_ENG_KEY = "category-name-eng";
  public static final String CATEGORY_NAME_CHI_KEY = "category-name-chi";
  public static final String ITEMS_KEY = "items";
  public static final String ITEM_SEQ_KEY = "item-seq";
  public static final String ITEM_NAME_ENG_KEY = "item-name-eng";
  public static final String ITEM_NAME_CHI_KEY = "item-name-chi";
  public static final String ITEM_LINK_ENG_KEY = "item-link-eng";
  public static final String ITEM_LINK_CHI_KEY = "item-link-chi";
  public static final String FILE_NAME_ENG_KEY = "file-name-eng";
  public static final String FILE_NAME_CHI_KEY = "file-name-chi";

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Category(
      @JsonProperty(CATEGORY_SEQ_KEY) ApimField<BigDecimal> categorySeq,
      @JsonProperty(CATEGORY_NAME_ENG_KEY) ApimField<String> categoryNameEng,
      @JsonProperty(CATEGORY_NAME_CHI_KEY) ApimField<String> categoryNameChi,
      @JsonProperty(ITEMS_KEY) ApimField<List<DocumentItem>> items) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record DocumentItem(
      @JsonProperty(ITEM_SEQ_KEY) ApimField<BigDecimal> itemSeq,
      @JsonProperty(ITEM_NAME_ENG_KEY) ApimField<String> itemNameEng,
      @JsonProperty(ITEM_NAME_CHI_KEY) ApimField<String> itemNameChi,
      @JsonProperty(ITEM_LINK_ENG_KEY) ApimField<String> itemLinkEng,
      @JsonProperty(ITEM_LINK_CHI_KEY) ApimField<String> itemLinkChi,
      @JsonProperty(FILE_NAME_ENG_KEY) ApimField<String> fileNameEng,
      @JsonProperty(FILE_NAME_CHI_KEY) ApimField<String> fileNameChi) {}
}
