package com.codeaudit.service;

import com.codeaudit.dto.DiffBlock;
import com.codeaudit.dto.ReviewFilter;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Diff 过滤服务
 * <p>
 * 根据 ReviewFilter 条件过滤 DiffBlock 列表，
 * 支持按路径（Ant 通配符）、文件扩展名过滤。
 * 作者和时间段过滤在 GitService 层通过 git log 实现。
 *
 * @author CodeAudit Team
 */
@Service
public class DiffFilterService {

    private final AntPathMatcher matcher = new AntPathMatcher();

    /**
     * 应用过滤条件到 DiffBlock 列表
     *
     * @param blocks 原始 diff 列表
     * @param filter 过滤条件（可为 null，null 时返回原列表）
     * @return 过滤后的 DiffBlock 列表
     */
    public List<DiffBlock> applyFilters(List<DiffBlock> blocks, ReviewFilter filter) {
        if (blocks == null || blocks.isEmpty()) {
            return blocks;
        }
        if (filter == null || !filter.hasAnyFilter()) {
            return blocks;
        }

        List<DiffBlock> result = new ArrayList<>(blocks);

        if (filter.getIncludePaths() != null && !filter.getIncludePaths().isEmpty()) {
            result = result.stream()
                    .filter(b -> matchesAny(b.filePath(), filter.getIncludePaths()))
                    .collect(Collectors.toList());
        }

        if (filter.getExcludePaths() != null && !filter.getExcludePaths().isEmpty()) {
            result = result.stream()
                    .filter(b -> !matchesAny(b.filePath(), filter.getExcludePaths()))
                    .collect(Collectors.toList());
        }

        if (filter.getIncludeExtensions() != null && !filter.getIncludeExtensions().isEmpty()) {
            result = result.stream()
                    .filter(b -> filter.getIncludeExtensions().stream()
                            .anyMatch(ext -> b.filePath().endsWith(ext)))
                    .collect(Collectors.toList());
        }

        return result;
    }

    private boolean matchesAny(String path, List<String> patterns) {
        for (String pattern : patterns) {
            if (matcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}
