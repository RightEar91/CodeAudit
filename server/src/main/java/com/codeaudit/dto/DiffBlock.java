package com.codeaudit.dto;

/**
 * Git diff 数据块 — 单文件 diff 的结构化封装
 * <p>
 * 由 GitDiffService 从 JGit 提取，每个 DiffBlock 代表一个 Java 文件的变更。
 * 审查引擎以 DiffBlock 为单位将 diff 内容送入 LLM 分析。
 *
 * @param filePath     文件路径（相对于仓库根目录）
 * @param changeType   变更类型（ADD / MODIFY / DELETE）
 * @param addedLines   新增行数
 * @param removedLines 删除行数
 * @param diffContent  unified diff 格式的完整变更内容
 *
 * @author CodeAudit Team
 */
public record DiffBlock(
        String filePath,
        String changeType,
        int addedLines,
        int removedLines,
        String diffContent
) {}
