package com.codeaudit.controller;

import com.codeaudit.common.Response;
import com.codeaudit.dto.StatisticsDTO;
import com.codeaudit.service.ExcelExportService;
import com.codeaudit.service.StatisticsService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Statistics REST controller for trend reports
 *
 * @author CodeAudit Team
 */
@RestController
@RequestMapping("/api")
public class StatisticsController {

    private final StatisticsService statisticsService;
    private final ExcelExportService excelExportService;

    public StatisticsController(StatisticsService statisticsService, ExcelExportService excelExportService) {
        this.statisticsService = statisticsService;
        this.excelExportService = excelExportService;
    }

    /**
     * Get project statistics including issue density trend, fix rate,
     * severity/category distribution and top authors.
     *
     * @param projectId project ID
     * @param days      optional, filter reviews within last N days
     */
    @GetMapping("/projects/{projectId}/statistics")
    public Response<StatisticsDTO> getStatistics(@PathVariable Long projectId,
                                                  @RequestParam(required = false) Integer days) {
        StatisticsDTO stats = statisticsService.getProjectStatistics(projectId, days);
        return Response.ok(stats);
    }

    /**
     * Export project statistics as Excel file
     */
    @GetMapping("/projects/{projectId}/statistics/excel")
    public ResponseEntity<byte[]> exportExcel(@PathVariable Long projectId,
                                               @RequestParam(required = false) Integer days) {
        StatisticsDTO stats = statisticsService.getProjectStatistics(projectId, days);
        byte[] excelBytes = excelExportService.exportStatistics(stats);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment",
                "codeaudit-statistics-" + projectId + ".xlsx");
        headers.setCacheControl("no-cache");
        return ResponseEntity.ok().headers(headers).body(excelBytes);
    }
}
