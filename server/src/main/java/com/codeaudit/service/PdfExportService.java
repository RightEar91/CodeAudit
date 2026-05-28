package com.codeaudit.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.codeaudit.common.BizException;
import com.codeaudit.entity.Issue;
import com.codeaudit.entity.Review;
import com.codeaudit.repository.IssueRepository;
import com.codeaudit.repository.ReviewRepository;

/**
 * PDF 审查报告导出服务
 * <p>
 * 使用 Apache PDFBox 生成格式化的代码审查报告 PDF 文件，
 * 包含审查元数据、统计汇总和详细问题列表。
 * 支持中文字体渲染，优先加载系统宋体/微软雅黑，降级使用内置英文字体。
 *
 * @author CodeAudit Team
 */
@Service
public class PdfExportService {

    private static final Logger log = LoggerFactory.getLogger(PdfExportService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final float MARGIN = 50;
    private static final float ROW_HEIGHT = 18;

    private final ReviewRepository reviewRepository;
    private final IssueRepository issueRepository;

    // 字体实例，在 generatePdf 中初始化后供内部类方法使用
    private PDFont font;
    private PDFont boldFont;

    public PdfExportService(ReviewRepository reviewRepository, IssueRepository issueRepository) {
        this.reviewRepository = reviewRepository;
        this.issueRepository = issueRepository;
    }

    public byte[] exportReviewPdf(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BizException(404, "审查不存在: " + reviewId));
        List<Issue> issues = issueRepository.findByReviewId(reviewId);
        return generatePdf(review, issues);
    }

    private byte[] generatePdf(Review review, List<Issue> issues) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            this.font = loadChineseFont(document);
            this.boldFont = font;

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            float y = drawHeader(document, page, review);
            PageContext lastCtx = drawIssuesSection(document, page, y, issues, review);

            String footerText = "报告生成时间：" + LocalDateTime.now().format(DATE_FMT);
            drawFooter(document, lastCtx.page, footerText);

            document.save(out);
            log.info("PDF 报告生成成功: reviewId={}, 问题数={}", review.getId(), issues.size());
            return out.toByteArray();

        } catch (IOException e) {
            log.error("PDF 生成失败: reviewId={}", review.getId(), e);
            throw new RuntimeException("PDF 生成失败", e);
        } finally {
            this.font = null;
            this.boldFont = null;
        }
    }

    private PDFont loadChineseFont(PDDocument document) {
        String[] fontPaths = {
                "C:/Windows/Fonts/simsun.ttc",
                "C:/Windows/Fonts/msyh.ttc",
                "C:/Windows/Fonts/simhei.ttf",
                "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
                "/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf",
                "/System/Library/Fonts/PingFang.ttc"
        };
        for (String path : fontPaths) {
            try {
                java.io.File fontFile = new java.io.File(path);
                if (fontFile.exists()) {
                    PDFont f = PDType0Font.load(document, fontFile);
                    log.info("PDF 中文字体加载成功: {}", path);
                    return f;
                }
            } catch (IOException ignored) {
            }
        }
        log.warn("未找到中文字体文件，使用内置英文字体（中文将无法正常显示）");
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    }

    /**
     * 绘制报告头部（标题 + 元数据），返回绘制后 Y 坐标
     */
    private float drawHeader(PDDocument document, PDPage page, Review review) throws IOException {
        float pageWidth = page.getMediaBox().getWidth();
        float y = page.getMediaBox().getHeight() - MARGIN;

        try (PDPageContentStream cs = new PDPageContentStream(document, page,
                PDPageContentStream.AppendMode.OVERWRITE, true)) {

            cs.beginText();
            cs.setFont(boldFont, 22);
            cs.setNonStrokingColor(0.1f, 0.3f, 0.7f);
            cs.newLineAtOffset(MARGIN, y);
            cs.showText("CodeAudit 代码审查报告");
            cs.endText();
            y -= 35;

            String projectName = review.getProject() != null ? review.getProject().getName() : "-";
            String repoPath = review.getProject() != null ? review.getProject().getRepoPath() : "-";
            String createdAt = review.getCreatedAt() != null
                    ? review.getCreatedAt().format(DATE_FMT) : "-";
            String durationStr = review.getDurationMs() != null
                    ? formatDuration(review.getDurationMs()) : "-";
            String statusLabel = switch (review.getStatus()) {
                case "completed" -> "已完成";
                case "processing" -> "进行中";
                case "failed" -> "失败";
                default -> "待处理";
            };

            String[][] meta = {
                    {"项目名称", projectName},
                    {"仓库路径", repoPath},
                    {"审查标题", review.getTitle()},
                    {"审查范围", review.getFromRef() + " .. " + review.getToRef()},
                    {"创建时间", createdAt},
                    {"审查耗时", durationStr},
                    {"审查状态", statusLabel}
            };

            cs.setFont(font, 10);
            cs.setNonStrokingColor(0.2f, 0.2f, 0.2f);
            for (String[] row : meta) {
                cs.beginText();
                cs.newLineAtOffset(MARGIN, y);
                cs.showText(row[0] + "：");
                cs.endText();

                cs.beginText();
                cs.newLineAtOffset(MARGIN + 80, y);
                cs.setNonStrokingColor(0.1f, 0.1f, 0.1f);
                cs.showText(row[1]);
                cs.setNonStrokingColor(0.2f, 0.2f, 0.2f);
                cs.endText();
                y -= 17;
            }
            y -= 10;

            cs.setNonStrokingColor(0.7f, 0.7f, 0.7f);
            cs.setLineWidth(0.5f);
            cs.moveTo(MARGIN, y);
            cs.lineTo(pageWidth - MARGIN, y);
            cs.stroke();
            y -= 20;
        }
        return y;
    }

    /**
     * 绘制统计摘要 + 问题详情表格，支持自动分页
     *
     * @return 最后一页底部的 Y 坐标
     */
    private PageContext drawIssuesSection(PDDocument document, PDPage firstPage,
                                     float startY, List<Issue> issues,
                                     Review review) throws IOException {
        PDPageContentStream firstCs = new PDPageContentStream(document, firstPage,
                PDPageContentStream.AppendMode.APPEND, true);
        PageContext ctx = new PageContext(firstPage, firstCs, startY);
        float y = ctx.y;

        float pageWidth = ctx.page.getMediaBox().getWidth();
        float[] colWidths = {60, 80, 130, 30, pageWidth - 2 * MARGIN - 60 - 80 - 130 - 30};
        String[] headers = {"严重程度", "分类", "文件", "行号", "问题描述与修复建议"};

        // 绘制统计摘要
        drawSummaryStatBlock(ctx.cs, y, pageWidth, review);
        y -= 58;

        ctx.cs.setFont(boldFont, 13);
        ctx.cs.setNonStrokingColor(0.1f, 0.3f, 0.7f);
        ctx.cs.beginText();
        ctx.cs.newLineAtOffset(MARGIN, y);
        ctx.cs.showText("问题详情");
        ctx.cs.endText();
        y -= 22;

        drawTableHeader(ctx.cs, MARGIN, y, colWidths, headers);
        y -= ROW_HEIGHT;

        ctx.cs.setFont(font, 9);
        for (Issue issue : issues) {
            if (y < MARGIN + 60) {
                ctx = newPage(document, ctx, MARGIN);
                y = ctx.y;
                drawTableHeader(ctx.cs, MARGIN, y, colWidths, headers);
                y -= ROW_HEIGHT;
                ctx.cs.setFont(font, 9);
            }
            drawIssueRow(ctx.cs, issue, MARGIN, y, colWidths);
            y -= ROW_HEIGHT;
        }
        ctx.cs.close();
        return ctx;
    }

    /**
     * 新建一页，关闭旧流，开新流
     */
    private PageContext newPage(PDDocument document, PageContext oldCtx, float y) throws IOException {
        oldCtx.cs.close();
        PDPage newPage = new PDPage(PDRectangle.A4);
        document.addPage(newPage);
        float pageHeight = newPage.getMediaBox().getHeight();
        float newY = pageHeight - MARGIN;
        PDPageContentStream newCs = new PDPageContentStream(document, newPage,
                PDPageContentStream.AppendMode.OVERWRITE, true);
        return new PageContext(newPage, newCs, newY);
    }

    private void drawSummaryStatBlock(PDPageContentStream cs, float y, float pageWidth,
                                       Review review) throws IOException {
        cs.setFont(boldFont, 13);
        cs.setNonStrokingColor(0.1f, 0.3f, 0.7f);
        cs.beginText();
        cs.newLineAtOffset(MARGIN, y);
        cs.showText("统计摘要");
        cs.endText();
        y -= 20;

        int total = review.getTotalIssues() != null ? review.getTotalIssues() : 0;
        int high = review.getHighCount() != null ? review.getHighCount() : 0;
        int medium = review.getMediumCount() != null ? review.getMediumCount() : 0;
        int low = review.getLowCount() != null ? review.getLowCount() : 0;

        cs.setFont(font, 11);
        cs.setNonStrokingColor(0.1f, 0.1f, 0.1f);
        cs.beginText();
        cs.newLineAtOffset(MARGIN + 10, y);
        cs.showText("问题总数: " + total + "    高危: " + high + "    中危: " + medium + "    低危: " + low);
        cs.endText();
        y -= 25;

        cs.setNonStrokingColor(0.7f, 0.7f, 0.7f);
        cs.setLineWidth(0.5f);
        cs.moveTo(MARGIN, y);
        cs.lineTo(pageWidth - MARGIN, y);
        cs.stroke();
    }

    private void drawTableHeader(PDPageContentStream cs, float margin, float y,
                                  float[] colWidths, String[] headers) throws IOException {
        float totalWidth = colWidths[0] + colWidths[1] + colWidths[2] + colWidths[3] + colWidths[4];
        cs.setNonStrokingColor(0.15f, 0.35f, 0.65f);
        cs.addRect(margin, y - 15, totalWidth, 15);
        cs.fill();

        cs.setFont(boldFont, 9);
        cs.setNonStrokingColor(1f, 1f, 1f);
        float x = margin + 3;
        for (int i = 0; i < headers.length; i++) {
            cs.beginText();
            cs.newLineAtOffset(x, y - 12);
            cs.showText(headers[i]);
            cs.endText();
            x += colWidths[i];
        }
        cs.setNonStrokingColor(0f, 0f, 0f);
    }

    private void drawIssueRow(PDPageContentStream cs, Issue issue, float margin,
                               float y, float[] colWidths) throws IOException {
        String severityLabel = switch (issue.getSeverity()) {
            case "HIGH" -> "高危";
            case "MEDIUM" -> "中危";
            case "LOW" -> "低危";
            default -> issue.getSeverity();
        };

        String categoryLabel = switch (issue.getCategory()) {
            case "SECURITY" -> "安全";
            case "PERFORMANCE" -> "性能";
            case "STYLE" -> "规范";
            case "BUG" -> "缺陷";
            default -> issue.getCategory();
        };

        String lineStr = issue.getLineNumber() != null ? String.valueOf(issue.getLineNumber()) : "-";
        String description = (issue.getMessage() != null ? issue.getMessage() : "")
                + (issue.getSuggestion() != null ? "  [建议] " + issue.getSuggestion() : "");
        if (description.length() > 120) {
            description = description.substring(0, 118) + "...";
        }

        String[] values = {severityLabel, categoryLabel, truncatePath(issue.getFilePath(), 28), lineStr, description};
        float x = margin + 3;
        for (int i = 0; i < values.length; i++) {
            cs.beginText();
            cs.newLineAtOffset(x, y - 12);
            cs.setNonStrokingColor(0.2f, 0.2f, 0.2f);
            cs.showText(values[i]);
            cs.endText();
            x += colWidths[i];
        }

        cs.setNonStrokingColor(0.85f, 0.85f, 0.85f);
        cs.setLineWidth(0.3f);
        float totalWidth = 0;
        for (float w : colWidths) totalWidth += w;
        cs.moveTo(margin, y - ROW_HEIGHT + 3);
        cs.lineTo(margin + totalWidth, y - ROW_HEIGHT + 3);
        cs.stroke();
    }

    private void drawFooter(PDDocument document, PDPage lastPage, String text) throws IOException {
        try (PDPageContentStream cs = new PDPageContentStream(document, lastPage,
                PDPageContentStream.AppendMode.APPEND, true)) {
            cs.setFont(font, 8);
            cs.setNonStrokingColor(0.6f, 0.6f, 0.6f);
            cs.beginText();
            cs.newLineAtOffset(MARGIN, MARGIN - 20);
            cs.showText(text);
            cs.endText();
        }
    }

    private String formatDuration(long millis) {
        if (millis < 1000) return millis + "ms";
        if (millis < 60_000) return String.format("%.1fs", millis / 1000.0);
        long seconds = millis / 1000;
        return String.format("%dm %ds", seconds / 60, seconds % 60);
    }

    private String truncatePath(String path, int maxLen) {
        if (path == null) return "-";
        if (path.length() <= maxLen) return path;
        return "..." + path.substring(path.length() - maxLen + 3);
    }

    /**
     * 页面上下文，封装当前页、内容流和 Y 坐标
     */
    private static class PageContext {
        final PDPage page;
        final PDPageContentStream cs;
        final float y;

        PageContext(PDPage page, PDPageContentStream cs, float y) {
            this.page = page;
            this.cs = cs;
            this.y = y;
        }
    }
}
