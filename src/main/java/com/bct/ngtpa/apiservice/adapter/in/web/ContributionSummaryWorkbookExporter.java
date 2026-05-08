package com.bct.ngtpa.apiservice.adapter.in.web;

import com.bct.ngtpa.apiservice.adapter.in.web.config.ContributionWebDisplayConfigProvider;
import com.bct.ngtpa.apiservice.application.dto.ContributionSummaryReportResult;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@RequiredArgsConstructor
public class ContributionSummaryWorkbookExporter {

    private final ContributionWebDisplayConfigProvider displayConfigProvider;

    public byte[] write(ContributionSummaryReportResult result) {
        try (var workbook = new XSSFWorkbook(); var outputStream = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Contribution Summary");
            var amountStyle = numericAmountStyle(workbook);
            writeHeaderRow(sheet.createRow(0), result);

            int rowIndex = 1;
            for (var reportRow : result.report().rows()) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(reportRow.dealingDate());
                row.createCell(1).setCellValue(reportRow.coveringPeriod());
                writeAmountCell(row, 2, reportRow.totalAmount(), amountStyle);

                int columnIndex = 3;
                for (var source : result.report().sources()) {
                    var amount = reportRow.amountsBySourceCode().get(source.code());
                    if (amount != null) {
                        writeAmountCell(row, columnIndex, amount, amountStyle);
                    } else {
                        row.createCell(columnIndex, CellType.BLANK);
                    }
                    columnIndex++;
                }
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to generate contribution summary workbook.", ex);
        }
    }

    private void writeHeaderRow(Row row, ContributionSummaryReportResult result) {
        var displayConfig = displayConfigProvider.get();
        row.createCell(0).setCellValue(displayConfig.dealingDateHeader());
        row.createCell(1).setCellValue(displayConfig.contributionPeriodHeader());
        row.createCell(2).setCellValue(displayConfig.totalContributionHeader());

        int columnIndex = 3;
        for (var source : result.report().sources()) {
            row.createCell(columnIndex++).setCellValue(
                    safe(source.labels().en()) + safe(source.labels().zh()));
        }
    }

    private void writeAmountCell(Row row, int columnIndex, BigDecimal amount, CellStyle amountStyle) {
        var cell = row.createCell(columnIndex);
        cell.setCellValue(amount.setScale(2, RoundingMode.HALF_UP).doubleValue());
        cell.setCellStyle(amountStyle);
    }

    private CellStyle numericAmountStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat dataFormat = workbook.createDataFormat();
        style.setDataFormat(dataFormat.getFormat("0.00"));
        return style;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}