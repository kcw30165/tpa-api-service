package com.bct.ngtpa.apiservice.adapter.in.web.fundprice.support;

import com.bct.ngtpa.apiservice.adapter.in.web.fundprice.config.FundPriceExportProperties;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceExportResult;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceRowResult;
import com.bct.ngtpa.apiservice.application.exception.FundPriceRetrievalException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.util.StringUtils;

/**
 * Generates the Fund Price XLSX export, per fund-price-excel-export-contract.md. Consumes {@link
 * FundPriceExportResult} DIRECTLY -- deliberately bypasses {@code FundPriceWebMapper} entirely,
 * since the web mapper collapses bid/offer into a single field and resolves fundName to one
 * language, both of which contradict this contract: currency and price always stay separate
 * columns, and Fund Name / 基金名稱 are always two permanently separate columns.
 *
 * <p>ONE sheet, named from {@code fund-price.export.sheet-names} by the cached {@code
 * fundPriceType}; a type with no configured sheet name fails as {@link
 * FundPriceRetrievalException#systemFailure()} (503). The sheet-name configuration is validated
 * in the constructor, so a bad value fails application startup.
 *
 * <p>Column set is selected by {@code fundPriceType}: UNIT PRICE = 5 columns, BIDOFFER PRICE = 7
 * columns. Price cells are NUMERIC; currency is a separate TEXT column. Decimal scale comes from
 * {@code unitPricePrecision}. The Chinese fund name cell is left BLANK when absent, never
 * backfilled from the English name. The Fund Price Date cell uses the configured month format when
 * the cached mode is MONTHLY, otherwise the configured full-date format.
 *
 * <p>Rows are written in the exact order received -- sorting is applied upstream by {@code
 * FundPriceSortingSupport}; this class does not re-sort.
 */
public class FundPriceWorkbookExporter {

  private static final String BIDOFFER_PRICE_TYPE = "BIDOFFER PRICE";
  private static final String MONTHLY_MODE = "MONTHLY";

  private final FundPriceExportProperties properties;
  private final DateTimeFormatter dateFormatter;
  private final DateTimeFormatter monthFormatter;

  public FundPriceWorkbookExporter(
      FundPriceExportProperties properties,
      DateTimeFormatter fundPriceFullDateFormatter,
      DateTimeFormatter fundPriceMonthFormatter) {
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
    this.dateFormatter =
        Objects.requireNonNull(fundPriceFullDateFormatter, "dateFormatter must not be null");
    this.monthFormatter =
        Objects.requireNonNull(fundPriceMonthFormatter, "monthFormatter must not be null");
    properties.validate();
  }

  public byte[] write(FundPriceExportResult result) {
    Objects.requireNonNull(result, "result must not be null");
    String sheetName = properties.sheetName(result.fundPriceType());
    if (sheetName == null) {
      throw FundPriceRetrievalException.systemFailure();
    }
    boolean bidOffer = BIDOFFER_PRICE_TYPE.equals(result.fundPriceType());
    DateTimeFormatter priceDateFormatter =
        MONTHLY_MODE.equals(result.mode()) ? monthFormatter : dateFormatter;
    try (var workbook = new XSSFWorkbook();
        var outputStream = new ByteArrayOutputStream()) {
      var sheet = workbook.createSheet(sheetName);
      var priceStyle = numericPriceStyle(workbook, result.unitPricePrecision());
      int scale = result.unitPricePrecision().intValueExact();

      writeHeaderRow(sheet.createRow(0), bidOffer);

      int rowIndex = 1;
      for (FundPriceRowResult row : result.rows()) {
        writeDataRow(
            sheet.createRow(rowIndex++), row, bidOffer, priceStyle, scale, priceDateFormatter);
      }

      workbook.write(outputStream);
      return outputStream.toByteArray();
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to generate fund price workbook.", ex);
    }
  }

  private void writeHeaderRow(Row row, boolean bidOffer) {
    var headers = properties.getHeaders();
    row.createCell(0).setCellValue(headers.getFundPriceDate());
    row.createCell(1).setCellValue(headers.getFundName());
    row.createCell(2).setCellValue(headers.getChineseFundName());
    if (bidOffer) {
      row.createCell(3).setCellValue(headers.getBidOffer().getBidCurrency());
      row.createCell(4).setCellValue(headers.getBidOffer().getBidPrice());
      row.createCell(5).setCellValue(headers.getBidOffer().getOfferCurrency());
      row.createCell(6).setCellValue(headers.getBidOffer().getOfferPrice());
    } else {
      row.createCell(3).setCellValue(headers.getUnitPrice().getCurrency());
      row.createCell(4).setCellValue(headers.getUnitPrice().getPrice());
    }
  }

  private void writeDataRow(
      Row row,
      FundPriceRowResult data,
      boolean bidOffer,
      CellStyle priceStyle,
      int scale,
      DateTimeFormatter priceDateFormatter) {
    row.createCell(0).setCellValue(data.priceDate().format(priceDateFormatter));
    row.createCell(1).setCellValue(data.fundName());
    writeChineseFundNameCell(row, data.chineseFundName());
    if (bidOffer) {
      row.createCell(3).setCellValue(data.fundCurrency());
      writePriceCell(row, 4, data.bidPrice(), priceStyle, scale);
      row.createCell(5).setCellValue(data.fundCurrency());
      writePriceCell(row, 6, data.offerPrice(), priceStyle, scale);
    } else {
      row.createCell(3).setCellValue(data.fundCurrency());
      writePriceCell(row, 4, data.bidPrice(), priceStyle, scale);
    }
  }

  private void writeChineseFundNameCell(Row row, String chineseFundName) {
    if (StringUtils.hasText(chineseFundName)) {
      row.createCell(2).setCellValue(chineseFundName);
    } else {
      row.createCell(2, CellType.BLANK);
    }
  }

  private void writePriceCell(Row row, int columnIndex, BigDecimal price, CellStyle style, int scale) {
    var cell = row.createCell(columnIndex);
    cell.setCellValue(price.setScale(scale, RoundingMode.HALF_UP).doubleValue());
    cell.setCellStyle(style);
  }

  private CellStyle numericPriceStyle(XSSFWorkbook workbook, java.math.BigInteger precision) {
    int scale = precision.intValueExact();
    String pattern = scale > 0 ? "0." + "0".repeat(scale) : "0";
    CellStyle style = workbook.createCellStyle();
    DataFormat dataFormat = workbook.createDataFormat();
    style.setDataFormat(dataFormat.getFormat(pattern));
    return style;
  }
}
