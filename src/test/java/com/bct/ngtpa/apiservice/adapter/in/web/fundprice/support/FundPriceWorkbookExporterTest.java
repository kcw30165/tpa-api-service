package com.bct.ngtpa.apiservice.adapter.in.web.fundprice.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.fundprice.config.FundPriceExportProperties;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceExportResult;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceRowResult;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Fail-first tests for FundPriceWorkbookExporter. Confirms the exact column layouts, header
 * sourcing, numeric price cells with precision-based scale, separate currency text columns, and
 * blank-when-absent Chinese fund name handling, per fund-price-excel-export-contract.md (script
 * 134's byte-exact dump).
 *
 * <p>Uses standard Apache POI reading APIs (org.apache.poi.ss.usermodel) to verify generated
 * bytes -- a well-known public library API, not an assumption about this codebase's internals.
 */
class FundPriceWorkbookExporterTest {

  private static final DateTimeFormatter FULL_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private FundPriceWorkbookExporter newExporter() {
    return new FundPriceWorkbookExporter(new FundPriceExportProperties(), FULL_DATE_FORMATTER);
  }

  private FundPriceRowResult row(
      String fundCode,
      String fundName,
      String chineseFundName,
      String currency,
      String bidPrice,
      String offerPrice,
      String riskLevel) {
    return new FundPriceRowResult(
        fundCode,
        fundName,
        chineseFundName,
        LocalDate.of(2026, 9, 2),
        currency,
        new BigDecimal(bidPrice),
        new BigDecimal(offerPrice),
        riskLevel);
  }

  @Test
  void unitPriceModeWritesExactlyFiveColumnsInConfirmedOrder() throws Exception {
    var result =
        new FundPriceExportResult(
            List.of(row("DUSM", "USD Fund", "\u7f8e\u5143\u57fa\u91d1", "HKD", "103.87", "103.87", "5")),
            "UNIT PRICE",
            "5",
            BigInteger.TWO);

    byte[] bytes = newExporter().write(result);

    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      var sheet = workbook.getSheetAt(0);
      Row header = sheet.getRow(0);
      assertThat(header.getLastCellNum()).isEqualTo((short) 5);
      assertThat(header.getCell(0).getStringCellValue()).contains("Fund Price Date");
      assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Fund Name");
      assertThat(header.getCell(2).getStringCellValue()).isEqualTo("\u57fa\u91d1\u540d\u7a31");
      assertThat(header.getCell(3).getStringCellValue()).contains("Unit Price Currency");
      assertThat(header.getCell(4).getStringCellValue()).contains("Unit Price");

      Row data = sheet.getRow(1);
      assertThat(data.getCell(1).getStringCellValue()).isEqualTo("USD Fund");
      assertThat(data.getCell(2).getStringCellValue()).isEqualTo("\u7f8e\u5143\u57fa\u91d1");
      assertThat(data.getCell(3).getStringCellValue()).isEqualTo("HKD");
      assertThat(data.getCell(4).getCellType()).isEqualTo(CellType.NUMERIC);
      assertThat(data.getCell(4).getNumericCellValue()).isEqualTo(103.87);
    }
  }

  @Test
  void bidOfferModeWritesExactlySevenColumnsInConfirmedOrder() throws Exception {
    var result =
        new FundPriceExportResult(
            List.of(row("DUSM", "USD Fund", "\u7f8e\u5143\u57fa\u91d1", "HKD", "103.87", "104.20", "5")),
            "BIDOFFER PRICE",
            "5",
            BigInteger.TWO);

    byte[] bytes = newExporter().write(result);

    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      var sheet = workbook.getSheetAt(0);
      Row header = sheet.getRow(0);
      assertThat(header.getLastCellNum()).isEqualTo((short) 7);
      assertThat(header.getCell(3).getStringCellValue()).contains("Bid Price Currency");
      assertThat(header.getCell(4).getStringCellValue()).contains("Bid Unit Price");
      assertThat(header.getCell(5).getStringCellValue()).contains("Offer Price Currency");
      assertThat(header.getCell(6).getStringCellValue()).contains("Offer Price");

      Row data = sheet.getRow(1);
      assertThat(data.getCell(3).getStringCellValue()).isEqualTo("HKD");
      assertThat(data.getCell(4).getNumericCellValue()).isEqualTo(103.87);
      assertThat(data.getCell(5).getStringCellValue()).isEqualTo("HKD");
      assertThat(data.getCell(6).getNumericCellValue()).isEqualTo(104.20);
    }
  }

  @Test
  void blankChineseFundNameIsWrittenAsBlankNeverBackfilledFromEnglish() throws Exception {
    var result =
        new FundPriceExportResult(
            List.of(row("DUSM", "HKD 1-Month Fixed Deposit", null, "HKD", "103.87", "103.87", null)),
            "UNIT PRICE",
            null,
            BigInteger.TWO);

    byte[] bytes = newExporter().write(result);

    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      var cell = workbook.getSheetAt(0).getRow(1).getCell(2);
      assertThat(cell == null || cell.getCellType() == CellType.BLANK || cell.getStringCellValue().isEmpty())
          .isTrue();
    }
  }

  @Test
  void numericPrecisionDrivesDecimalScale() throws Exception {
    var result =
        new FundPriceExportResult(
            List.of(row("DUSM", "USD Fund", "n", "HKD", "103.8712345", "103.8712345", "5")),
            "UNIT PRICE",
            "5",
            BigInteger.valueOf(4));

    byte[] bytes = newExporter().write(result);

    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      var cell = workbook.getSheetAt(0).getRow(1).getCell(4);
      assertThat(cell.getCellStyle().getDataFormatString()).contains("0.0000");
    }
  }

  @Test
  void writesOneRowPerInputRowInReceivedOrderNoReSorting() throws Exception {
    var result =
        new FundPriceExportResult(
            List.of(
                row("Z", "Zebra Fund", "n", "HKD", "1.0", "1.0", "3"),
                row("A", "Alpha Fund", "n", "HKD", "2.0", "2.0", "1")),
            "UNIT PRICE",
            "3",
            BigInteger.TWO);

    byte[] bytes = newExporter().write(result);

    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      var sheet = workbook.getSheetAt(0);
      assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Zebra Fund");
      assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("Alpha Fund");
    }
  }
}
