package com.codeaudit.service;

import com.codeaudit.dto.StatisticsDTO;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Excel export service for statistics reports
 *
 * @author CodeAudit Team
 */
@Service
public class ExcelExportService {

    private static final Logger log = LoggerFactory.getLogger(ExcelExportService.class);

    /**
     * Export statistics as Excel workbook bytes
     */
    public byte[] exportStatistics(StatisticsDTO stats) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            createSummarySheet(wb, stats);
            createTrendSheet(wb, stats);
            createAuthorSheet(wb, stats);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Excel 导出失败", e);
            throw new RuntimeException("Excel 导出失败: " + e.getMessage(), e);
        }
    }

    private void createSummarySheet(Workbook wb, StatisticsDTO stats) {
        Sheet sheet = wb.createSheet("汇总");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("指标");
        header.createCell(1).setCellValue("数值");

        StatisticsDTO.FixRate fr = stats.getFixRate();
        int rowIdx = 1;
        if (fr != null) {
            createRow(sheet, rowIdx++, "修复率", fr.getRate() + "%");
            createRow(sheet, rowIdx++, "已修复", String.valueOf(fr.getResolved()));
            createRow(sheet, rowIdx++, "未处理", String.valueOf(fr.getOpen()));
            createRow(sheet, rowIdx++, "已忽略", String.valueOf(fr.getIgnored()));
        }
        rowIdx++;

        for (StatisticsDTO.SeverityItem si : stats.getSeverityDistribution()) {
            createRow(sheet, rowIdx++, "严重程度-" + si.getSeverity(), String.valueOf(si.getCount()));
        }
        rowIdx++;

        for (StatisticsDTO.CategoryItem ci : stats.getCategoryDistribution()) {
            createRow(sheet, rowIdx++, "分类-" + ci.getCategory(), String.valueOf(ci.getCount()));
        }
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void createTrendSheet(Workbook wb, StatisticsDTO stats) {
        Sheet sheet = wb.createSheet("问题密度趋势");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("日期");
        header.createCell(1).setCellValue("问题数");
        header.createCell(2).setCellValue("文件数");

        int rowIdx = 1;
        for (StatisticsDTO.TrendPoint tp : stats.getIssueDensityTrend()) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(tp.getDate());
            row.createCell(1).setCellValue(tp.getIssueCount());
            row.createCell(2).setCellValue(tp.getFileCount());
        }
        sheet.autoSizeColumn(0);
    }

    private void createAuthorSheet(Workbook wb, StatisticsDTO stats) {
        Sheet sheet = wb.createSheet("作者排行");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("作者");
        header.createCell(1).setCellValue("邮箱");
        header.createCell(2).setCellValue("问题数");

        int rowIdx = 1;
        for (StatisticsDTO.AuthorStat as : stats.getTopAuthors()) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(as.getAuthorName());
            row.createCell(1).setCellValue(as.getAuthorEmail());
            row.createCell(2).setCellValue(as.getIssueCount());
        }
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void createRow(Sheet sheet, int rowIdx, String label, String value) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
    }
}
