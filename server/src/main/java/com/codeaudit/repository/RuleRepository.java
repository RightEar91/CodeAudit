package com.codeaudit.repository;

import com.codeaudit.entity.Rule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 审查规则数据访问层
 * <p>
 * 提供对 ca_rules 表的 CRUD 及相关查询，支持按启用状态、分类、语言筛选。
 *
 * @author CodeAudit Team
 */
@Repository
public interface RuleRepository extends JpaRepository<Rule, Long> {

    /**
     * 查询所有已启用的规则（审查时注入 Prompt 的规则集合）
     */
    List<Rule> findByIsEnabledTrue();

    /**
     * 按分类查询规则（SECURITY / PERFORMANCE / STYLE / BUG）
     */
    List<Rule> findByCategory(String category);

    /**
     * 按分类分页查询规则
     */
    Page<Rule> findByCategory(String category, Pageable pageable);

    /**
     * 按语言查询所有已启用的规则
     */
    List<Rule> findByLanguageAndIsEnabledTrue(String language);
}
