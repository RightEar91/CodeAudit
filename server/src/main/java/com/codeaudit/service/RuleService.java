package com.codeaudit.service;

import com.codeaudit.common.BizException;
import com.codeaudit.entity.Rule;
import com.codeaudit.repository.RuleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    // ==================== 规则模板包 ====================

    private static final Map<String, List<Rule>> TEMPLATE_PACKS = new LinkedHashMap<>();

    static {
        // Java 阿里巴巴规范包
        List<Rule> alibaba = new ArrayList<>();
        alibaba.add(buildTemplateRule("命名规范-类名", "STYLE", "Java",
                "类名使用 UpperCamelCase 风格，必须遵从驼峰形式",
                "检查类名是否使用 UpperCamelCase 风格（首字母大写驼峰），如 ForceCode。"));
        alibaba.add(buildTemplateRule("命名规范-方法名", "STYLE", "Java",
                "方法名使用 lowerCamelCase 风格，必须遵从驼峰形式",
                "检查方法名是否使用 lowerCamelCase 风格（首字母小写驼峰），如 getUserById。"));
        alibaba.add(buildTemplateRule("集合处理", "BUG", "Java",
                "使用集合转数组必须使用带参数的 toArray(T[] array)",
                "检查是否使用 toArray() 无参方法，应使用 toArray(new String[0]) 形式。"));
        alibaba.add(buildTemplateRule("异常处理", "BUG", "Java",
                "不要在 finally 块中使用 return，finally 块中的 return 会吞掉异常",
                "检查 finally 块中是否存在 return 语句。"));
        alibaba.add(buildTemplateRule("并发安全-锁", "SECURITY", "Java",
                "SimpleDateFormat 是线程不安全的，应使用 ThreadLocal 或 DateTimeFormatter",
                "检查是否在静态字段或共享变量中使用 SimpleDateFormat。"));
        alibaba.add(buildTemplateRule("日志规范", "STYLE", "Java",
                "应用中不可直接使用日志系统（Log4j、Logback）中的 API，应使用 SLF4J",
                "检查是否直接导入 org.apache.log4j 或 ch.qos.logback。"));
        alibaba.add(buildTemplateRule("SQL规范-模糊查询", "SECURITY", "Java",
                "禁止使用 ${} 占位符，应使用 #{} 防止 SQL 注入",
                "检查 MyBatis XML 或注解中是否使用 ${} 而非 #{} 占位符。"));
        TEMPLATE_PACKS.put("java-alibaba", alibaba);

        // OWASP Top 10 安全包
        List<Rule> owasp = new ArrayList<>();
        owasp.add(buildTemplateRule("SQL注入防护", "SECURITY", "Java",
                "所有数据库查询必须使用参数化查询或 PreparedStatement，禁止字符串拼接",
                "检查是否存在 SQL 字符串拼接、MyBatis ${} 占位符、JDBC Statement 拼接等 SQL 注入风险。"));
        owasp.add(buildTemplateRule("XSS 跨站脚本防护", "SECURITY", "Java",
                "所有用户输入输出必须经过编码/转义处理",
                "检查返回给前端的用户内容是否经过 HTML 编码，URL 参数是否编码，JavaScript 上下文是否转义。"));
        owasp.add(buildTemplateRule("认证与会话管理", "SECURITY", "Java",
                "密码不可明文存储、Session 需设置超时、敏感操作需二次认证",
                "检查密码是否使用 BCrypt/SCrypt 加密、Session 是否有合理过期时间、敏感操作有无权限校验。"));
        owasp.add(buildTemplateRule("敏感信息泄露", "SECURITY", "Java",
                "日志、错误信息、API 响应中不能包含密码、Token、密钥等敏感信息",
                "检查 log.info/error 中是否打印了密码、Token、证书私钥等敏感信息。"));
        owasp.add(buildTemplateRule("XXE 外部实体注入", "SECURITY", "Java",
                "XML 解析器必须禁用 DTD 和外部实体",
                "检查 DocumentBuilderFactory、SAXParserFactory 等 XML 解析器是否设置 FEATURE_DISALLOW_DOCTYPE_DECL。"));
        owasp.add(buildTemplateRule("SSRF 服务端请求伪造", "SECURITY", "Java",
                "服务端发起的 HTTP 请求必须校验目标地址合法性",
                "检查是否对用户传入的 URL/域名进行白名单校验，防止访问内网地址。"));
        owasp.add(buildTemplateRule("路径遍历", "SECURITY", "Java",
                "文件路径必须做校验，防止 ../ 路径遍历攻击",
                "检查文件读取/写入操作中的路径是否包含 ../ 或未经过滤的用户输入。"));
        TEMPLATE_PACKS.put("owasp-top10", owasp);

        // Python PEP8 规范包
        List<Rule> pep8 = new ArrayList<>();
        pep8.add(buildTemplateRule("缩进规范", "STYLE", "Python",
                "每个缩进级别使用 4 个空格，禁止混用空格和 Tab",
                "检查是否使用 4 空格缩进，是否存在 Tab 和空格混用的情况。"));
        pep8.add(buildTemplateRule("行长度限制", "STYLE", "Python",
                "每行最多 79 个字符，文档字符串/注释最多 72 字符",
                "检查代码行是否超过 79 字符、注释和文档字符串是否超过 72 字符。"));
        pep8.add(buildTemplateRule("空行规范", "STYLE", "Python",
                "顶级函数和类定义之间用 2 个空行分隔，类内方法之间用 1 个空行",
                "检查顶级定义前后是否有 2 个空行、类内方法前后是否有 1 个空行。"));
        pep8.add(buildTemplateRule("导入规范", "STYLE", "Python",
                "每个导入独占一行，按标准库、第三方库、本地库顺序分组",
                "检查导入语句是否按分组排序、是否使用 from module import * 通配符导入。"));
        pep8.add(buildTemplateRule("命名规范", "STYLE", "Python",
                "函数/变量使用 snake_case，类名使用 CapWords，常量使用 UPPER_CASE",
                "检查命名是否符合 PEP8 规范：函数 snake_case、类 CapWords、异常 CapWords、常量 UPPER_CASE。"));
        pep8.add(buildTemplateRule("异常处理", "BUG", "Python",
                "禁止使用裸 except:，应明确捕获具体异常类型",
                "检查是否存在 bare except 语句，应指定具体异常类型如 except ValueError。"));
        pep8.add(buildTemplateRule("可变默认参数", "BUG", "Python",
                "禁止使用可变对象作为函数默认参数",
                "检查函数默认参数是否使用 [] 或 {} 等可变对象，应使用 None 并在函数内初始化。"));
        TEMPLATE_PACKS.put("python-pep8", pep8);

        // Kotlin 最佳实践包
        List<Rule> kotlin = new ArrayList<>();
        kotlin.add(buildTemplateRule("空安全规范", "BUG", "Kotlin",
                "避免使用 !! 操作符，优先使用 ?. 和 ?: 安全调用",
                "检查是否使用 !! 强制非空断言，应使用安全调用 ?. 或 Elvis 操作符 ?: 配合默认值。"));
        kotlin.add(buildTemplateRule("数据类规范", "STYLE", "Kotlin",
                "优先使用 data class 声明纯数据模型，避免使用普通 class",
                "检查只包含属性且无业务逻辑的 class 是否可改为 data class。"));
        kotlin.add(buildTemplateRule("协程安全", "SECURITY", "Kotlin",
                "协程中禁止阻塞调用，避免在主线程使用 runBlocking",
                "检查 suspend 函数中是否调用了 Thread.sleep、synchronized 等阻塞操作，应使用 delay、Mutex。"));
        kotlin.add(buildTemplateRule("密封类使用", "STYLE", "Kotlin",
                "使用 sealed class/sealed interface 代替 enum 表达有限状态类型",
                "检查 when 表达式中是否遗漏分支、状态类型是否适合用 sealed class 替代 enum。"));
        kotlin.add(buildTemplateRule("作用域函数", "STYLE", "Kotlin",
                "合理使用 let/run/with/apply/also 作用域函数，避免过度链式嵌套",
                "检查作用域函数链式调用是否超过 3 层，是否需要提取中间变量提高可读性。"));
        kotlin.add(buildTemplateRule("集合操作", "PERFORMANCE", "Kotlin",
                "使用 Kotlin 集合扩展函数（filter/map/fold）代替显式 for 循环",
                "检查是否使用手动循环处理集合而忽略了简洁的扩展函数（但需注意链式操作的中间集合开销）。"));
        kotlin.add(buildTemplateRule("默认参数", "STYLE", "Kotlin",
                "优先使用默认参数代替方法重载",
                "检查是否存在仅参数数量不同的多个重载方法，可合并为带默认参数的单方法。"));
        TEMPLATE_PACKS.put("kotlin-best", kotlin);

        // Rust 最佳实践包
        List<Rule> rustPack = new ArrayList<>();
        rustPack.add(buildTemplateRule("所有权与借用", "BUG", "Rust",
                "避免不必要的 clone()，优先使用引用借用（&）",
                "检查是否在不必要时调用了 .clone()，应使用引用传递 &T 或依赖 Copy trait。"));
        rustPack.add(buildTemplateRule("错误处理", "BUG", "Rust",
                "避免使用 unwrap()/expect() 在生产代码中，使用 ? 操作符传播错误",
                "检查是否存在 .unwrap() 或 .expect() 调用，应改用 ? 操作符或 match 匹配 Result。"));
        rustPack.add(buildTemplateRule("unsafe 代码", "SECURITY", "Rust",
                "unsafe 代码块应最小化并添加 SAFETY 注释说明安全性保证",
                "检查 unsafe 块是否可以进行安全封装、是否缺少 SAFETY 注释说明不变量。"));
        rustPack.add(buildTemplateRule("资源管理", "BUG", "Rust",
                "确保 Drop trait 正确实现，防止资源泄漏",
                "检查手动管理资源的 struct 是否正确实现 Drop trait、是否存在文件句柄/锁泄漏风险。"));
        rustPack.add(buildTemplateRule("并发安全", "SECURITY", "Rust",
                "正确使用 Mutex/RwLock，避免死锁和数据竞争",
                "检查 Mutex 锁是否在函数调用期间持有过久、是否在持有锁时调用可能阻塞的操作。"));
        rustPack.add(buildTemplateRule("性能优化", "PERFORMANCE", "Rust",
                "优先使用迭代器（iter）代替索引循环，使用 collect 前考虑惰性求值",
                "检查是否使用 for i in 0..len 索引循环代替迭代器、是否存在不必要的 collect 调用。"));
        rustPack.add(buildTemplateRule("生命周期标注", "STYLE", "Rust",
                "生命周期标注应简洁，遵循编译器省略规则",
                "检查是否存在可以省略的显式生命周期标注、Elision Rule 可以覆盖的场景。"));
        TEMPLATE_PACKS.put("rust-best", rustPack);
    }

    private static Rule buildTemplateRule(String name, String category, String language,
                                          String description, String prompt) {
        return Rule.builder()
                .name(name)
                .category(category)
                .language(language)
                .description(description)
                .prompt(prompt)
                .isBuiltin(false)
                .isEnabled(true)
                .build();
    }

    /**
     * 获取所有可用的规则模板包列表
     */
    public List<Map<String, Object>> listTemplates() {
        List<Map<String, Object>> templates = new ArrayList<>();
        for (var entry : TEMPLATE_PACKS.entrySet()) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("key", entry.getKey());
            info.put("name", entry.getValue().get(0) != null
                    ? switch (entry.getKey()) {
                        case "java-alibaba" -> "Java 阿里巴巴规范包";
                        case "owasp-top10" -> "OWASP 安全 Top 10 包";
                        case "python-pep8" -> "Python PEP8 规范包";
                        case "kotlin-best" -> "Kotlin 最佳实践包";
                        case "rust-best" -> "Rust 最佳实践包";
                        default -> entry.getKey();
                    }
                    : entry.getKey());
            info.put("description", entry.getValue().get(0) != null
                    ? switch (entry.getKey()) {
                        case "java-alibaba" -> "基于阿里巴巴 Java 开发手册，涵盖命名、集合、并发、异常等 7 条核心规则";
                        case "owasp-top10" -> "基于 OWASP 十大 Web 安全风险，涵盖 SQL 注入、XSS、SSRF 等 7 条安全规则";
                        case "python-pep8" -> "基于 Python PEP8 官方编码风格指南，涵盖缩进、命名、导入等 7 条规范规则";
                        case "kotlin-best" -> "基于 Kotlin 官方编码规范，涵盖空安全、协程、作用域函数等 7 条核心规则";
                        case "rust-best" -> "基于 Rust 官方最佳实践，涵盖所有权、错误处理、并发安全等 7 条核心规则";
                        default -> "";
                    }
                    : "");
            info.put("ruleCount", entry.getValue().size());
            info.put("language", entry.getValue().get(0).getLanguage());
            templates.add(info);
        }
        return templates;
    }

    /**
     * 导入规则模板包
     *
     * @param templateKey 模板包标识（java-alibaba / owasp-top10 / python-pep8）
     * @return 导入的规则列表
     * @throws BizException 模板不存在时抛出（404）
     */
    @Transactional
    public List<Rule> importTemplate(String templateKey) {
        List<Rule> templateRules = TEMPLATE_PACKS.get(templateKey);
        if (templateRules == null) {
            throw new BizException(404, "规则模板不存在: " + templateKey);
        }
        List<Rule> saved = new ArrayList<>();
        for (Rule rule : templateRules) {
            Rule copy = Rule.builder()
                    .name(rule.getName())
                    .category(rule.getCategory())
                    .language(rule.getLanguage())
                    .description(rule.getDescription())
                    .prompt(rule.getPrompt())
                    .isBuiltin(false)
                    .isEnabled(true)
                    .build();
            saved.add(ruleRepository.save(copy));
        }
        return saved;
    }
}
