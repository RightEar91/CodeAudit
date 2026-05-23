package com.codeaudit.service;

import com.codeaudit.common.BizException;
import com.codeaudit.entity.Rule;
import com.codeaudit.repository.RuleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 审查规则管理服务
 * <p>
 * 提供规则的 CRUD、启用/禁用、分类筛选和分页查询。
 * 内置规则（is_builtin=true）不可删除。
 *
 * @author CodeAudit Team
 */
@Service
public class RuleService {

    private final RuleRepository ruleRepository;

    public RuleService(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    /**
     * 分页获取规则列表，可选按分类筛选
     */
    public Page<Rule> list(String category, Pageable pageable) {
        if (category != null && !category.isBlank()) {
            return ruleRepository.findByCategory(category, pageable);
        }
        return ruleRepository.findAll(pageable);
    }

    /**
     * 获取所有已启用的规则（审查时注入 Prompt 的规则集合）
     */
    public List<Rule> listEnabled() {
        return ruleRepository.findByIsEnabledTrue();
    }

    /**
     * 按 ID 查询单条规则
     */
    public Optional<Rule> findById(Long id) {
        return ruleRepository.findById(id);
    }

    /**
     * 创建自定义规则
     * <p>
     * 新规则默认 is_builtin=false, is_enabled=true。
     */
    @Transactional
    public Rule create(Rule rule) {
        rule.setId(null);
        rule.setIsBuiltin(false);
        return ruleRepository.save(rule);
    }

    /**
     * 更新规则（仅更新传入的非空字段）
     *
     * @throws BizException 规则不存在时抛出（404）
     */
    @Transactional
    public Rule update(Long id, Rule updated) {
        Rule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "规则不存在: " + id));
        if (updated.getName() != null) rule.setName(updated.getName());
        if (updated.getCategory() != null) rule.setCategory(updated.getCategory());
        if (updated.getLanguage() != null) rule.setLanguage(updated.getLanguage());
        if (updated.getDescription() != null) rule.setDescription(updated.getDescription());
        if (updated.getPrompt() != null) rule.setPrompt(updated.getPrompt());
        if (updated.getIsEnabled() != null) rule.setIsEnabled(updated.getIsEnabled());
        return ruleRepository.save(rule);
    }

    /**
     * 启用/禁用规则
     *
     * @throws BizException 规则不存在时抛出（404）
     */
    @Transactional
    public Rule toggle(Long id, Boolean isEnabled) {
        Rule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "规则不存在: " + id));
        rule.setIsEnabled(isEnabled != null ? isEnabled : !rule.getIsEnabled());
        return ruleRepository.save(rule);
    }

    /**
     * 删除规则
     * <p>
     * 内置规则（is_builtin=true）不可删除。
     *
     * @throws BizException 规则不存在（404）或为内置规则（400）
     */
    @Transactional
    public void delete(Long id) {
        Rule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "规则不存在: " + id));
        if (rule.getIsBuiltin()) {
            throw new BizException("内置规则不可删除");
        }
        ruleRepository.delete(rule);
    }
}
