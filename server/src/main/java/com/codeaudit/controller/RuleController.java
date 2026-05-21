package com.codeaudit.controller;

import com.codeaudit.common.BizException;
import com.codeaudit.common.Response;
import com.codeaudit.entity.Rule;
import com.codeaudit.service.RuleService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 审查规则管理 REST 控制器
 * <p>
 * 提供规则的 CRUD、启用/禁用和分类筛选接口。
 * 内置规则（is_builtin=true）不可删除但可启用/禁用。
 * 所有响应统一封装为 {@link Response} 格式。
 * <p>
 * 路由设计：
 * <ul>
 *   <li>{@code GET    /api/rules}            — 规则列表（支持按分类筛选）</li>
 *   <li>{@code GET    /api/rules/enabled}    — 获取已启用的规则</li>
 *   <li>{@code GET    /api/rules/:id}        — 查询单条规则</li>
 *   <li>{@code POST   /api/rules}            — 创建自定义规则</li>
 *   <li>{@code PUT    /api/rules/:id}        — 更新规则</li>
 *   <li>{@code PUT    /api/rules/:id/toggle} — 启用/禁用规则</li>
 *   <li>{@code DELETE /api/rules/:id}        — 删除规则（内置规则不可删）</li>
 * </ul>
 *
 * @author CodeAudit Team
 */
@RestController
@RequestMapping("/api/rules")
public class RuleController {

    private final RuleService ruleService;

    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    /**
     * 获取规则列表，可选按分类筛选（SECURITY / PERFORMANCE / STYLE / BUG），支持分页
     * <p>
     * 分页参数示例：?page=0&size=20&sort=createdAt,desc
     */
    @GetMapping
    public Response<Page<Rule>> list(@RequestParam(required = false) String category, Pageable pageable) {
        Page<Rule> rules = ruleService.list(category, pageable);
        return Response.ok(rules);
    }

    /**
     * 获取所有已启用的规则（审查时注入 Prompt 的规则集合）
     */
    @GetMapping("/enabled")
    public Response<List<Rule>> listEnabled() {
        return Response.ok(ruleService.listEnabled());
    }

    /**
     * 按 ID 查询单条规则
     *
     * @throws BizException 规则不存在时由全局异常处理器拦截（404）
     */
    @GetMapping("/{id}")
    public Response<Rule> getById(@PathVariable Long id) {
        Rule rule = ruleService.findById(id)
                .orElseThrow(() -> new BizException(404, "规则不存在: " + id));
        return Response.ok(rule);
    }

    /**
     * 创建自定义规则
     * <p>
     * 新规则默认 is_builtin=false, is_enabled=true。
     */
    @PostMapping
    public Response<Rule> create(@Valid @RequestBody Rule rule) {
        Rule saved = ruleService.create(rule);
        return Response.created(saved);
    }

    /**
     * 更新规则（仅更新传入的非空字段）
     *
     * @throws BizException 规则不存在时由全局异常处理器拦截（404）
     */
    @PutMapping("/{id}")
    public Response<Rule> update(@PathVariable Long id, @RequestBody Rule rule) {
        Rule updated = ruleService.update(id, rule);
        return Response.ok(updated);
    }

    /**
     * 启用/禁用规则
     * <p>
     * 请求体：{@code {"isEnabled": true}} 或 {@code {"isEnabled": false}}
     *
     * @throws BizException 规则不存在时由全局异常处理器拦截（404）
     */
    @PutMapping("/{id}/toggle")
    public Response<Rule> toggle(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Rule rule = ruleService.toggle(id, body.get("isEnabled"));
        return Response.ok(rule);
    }

    /**
     * 删除规则
     * <p>
     * 内置规则（is_builtin=true）不可删除。
     *
     * @throws BizException 规则不存在（404）或为内置规则（400）
     */
    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable Long id) {
        ruleService.delete(id);
        return Response.ok();
    }
}
