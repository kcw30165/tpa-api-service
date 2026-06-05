package com.bct.ngtpa.apiservice.adapter.in.web.support;

import com.bct.ngtpa.apiservice.adapter.in.web.config.ContributionWebDisplayConfigProvider;
import com.bct.ngtpa.apiservice.application.dto.CurrencyDisplay;
import com.bct.ngtpa.apiservice.application.dto.ContributionSummaryReportResult;
import com.bct.ngtpa.apiservice.adapter.in.web.config.ContributionSummaryProperties;
import com.bct.ngtpa.apiservice.domain.model.ContributionLabels;
import com.bct.ngtpa.apiservice.domain.model.ContributionSource;
import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryReport;
import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryRow;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContributionSummaryWorkbookExporterTest {

    @Test
    void writesConfiguredHeadersNumericCellsAndLeavesMissingSourceBlank() throws Exception {
        var exporter = new ContributionSummaryWorkbookExporter(
                new ContributionWebDisplayConfigProvider(new ContributionSummaryProperties()));
        var report = new ContributionSummaryReportResult(
                new ContributionSummaryReport(
                        "HKD",
                        List.of(
                                new ContributionSource("ER", new ContributionLabels("Company", ""), 10),
                                new ContributionSource("EE", new ContributionLabels("Member", ""), 20)),
                        List.of(
                                new ContributionSummaryRow(
                                        "01/03/2026",
                                        "01/03/2026",
                                        "31/03/2026",
                                        new BigDecimal("24908.45"),
                                        new LinkedHashMap<>(java.util.Map.of(
                                                "ER", new BigDecimal("17791.75"),
                                                "EE", new BigDecimal("7116.7")))),
                                new ContributionSummaryRow(
                                        "01/02/2026",
                                        "01/02/2026",
                                        "28/02/2026",
                                        new BigDecimal("400.00"),
                                        new LinkedHashMap<>(java.util.Map.of(
                                                "EE", new BigDecimal("400.00")))))),
                new CurrencyDisplay("HKD", "港元"),
                null,
                "",
                "",
                "");

        byte[] bytes = exporter.write(report);

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            var headerRow = sheet.getRow(0);
            assertEquals("Dealing date處理日期", headerRow.getCell(0).getStringCellValue());
            assertEquals("Contribution Periods供款期", headerRow.getCell(1).getStringCellValue());
            assertEquals("Total Contributions供款總額", headerRow.getCell(2).getStringCellValue());
            assertEquals("Company", headerRow.getCell(3).getStringCellValue());
            assertEquals("Member", headerRow.getCell(4).getStringCellValue());

            var firstDataRow = sheet.getRow(1);
            assertEquals("01/03/2026", firstDataRow.getCell(0).getStringCellValue());
            assertEquals("01/03/2026 - 31/03/2026", firstDataRow.getCell(1).getStringCellValue());
            assertEquals(24908.45d, firstDataRow.getCell(2).getNumericCellValue());
            assertEquals("0.00", firstDataRow.getCell(2).getCellStyle().getDataFormatString());
            assertEquals(17791.75d, firstDataRow.getCell(3).getNumericCellValue());
            assertEquals(7116.70d, firstDataRow.getCell(4).getNumericCellValue());

            var secondDataRow = sheet.getRow(2);
            assertEquals(CellType.BLANK, secondDataRow.getCell(3).getCellType());
            assertTrue(secondDataRow.getCell(4).getNumericCellValue() > 0);
        }
    }
}