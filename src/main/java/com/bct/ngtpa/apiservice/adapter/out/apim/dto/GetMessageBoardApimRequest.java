package com.bct.ngtpa.apiservice.adapter.out.apim.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetMessageBoardApimRequest {

    private static final DateTimeFormatter REF_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @JsonProperty("policy-no")
    private String policyNo;

    @JsonProperty("cert-no")
    private String certNo;

    @JsonProperty("user-id")
    private String userId;

    @Builder.Default
    @JsonProperty("ref-date")
    @JsonSetter(nulls = Nulls.SKIP)
    private String refDate = currentRefDate();

    @JsonProperty("env")
    private String accountEnv;

    @JsonProperty("mbr-type")
    private String mbrType;

    public String getRefDate() {
        if (refDate == null || refDate.isBlank()) {
            refDate = currentRefDate();
        }
        return refDate;
    }

    public void setRefDate(String refDate) {
        this.refDate = (refDate == null || refDate.isBlank()) ? currentRefDate() : refDate;
    }

    private static String currentRefDate() {
        return LocalDate.now().format(REF_DATE_FORMATTER);
    }
}
