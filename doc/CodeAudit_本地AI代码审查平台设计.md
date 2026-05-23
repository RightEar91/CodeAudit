# CodeAudit — 本地AI代码审查平台

> 项目代号：CodeAudit
> 定位：代码不出内网，AI本地审查，结果可控

---

## 一、产品定位

| 维度 | 说明 |
|------|------|
| 核心卖点 | **代码完全本地处理**，不上传任何云端服务，杜绝源码泄露风险 |
| 目标用户 | 中小开发团队、信创/军工企业、对个人代码安全敏感的开发者 |
| 竞品差异 | SonarQube规则死/太重，Copilot/通义灵码传源码训练，CodeAudit本地AI审查+轻量Web |
| 商业模式 | 核心功能开源免费（口碑）→ 团队版$19/月（协作+自定义规则）→ 企业版$99/人/年（CI/CD集成+审计） |

---

## 二、MVP功能集（4-6周可交付）

### 2.1 核心流程

```
用户上传Git仓库 或 粘贴代码片段
    ↓
系统提取Diff / 扫描文件
    ↓
本地Ollama（Qwen3:8b）分析代码
    ↓
生成审查报告（漏洞/性能/规范）
    ↓
Web界面展示，可导出PDF/JSON
```

### 2.2 MVP功能列表

| 功能 | 优先级 | 说明 |
|------|--------|------|
| **Git仓库接入** | P0 | 支持本地Git仓库路径扫描，自动提取最新commit的diff |
| **代码Diff审查** | P0 | 对diff文件逐块送LLM审查，返回问题列表 |
| **Web管理台** | P0 | Vue3界面：项目列表、审查记录、报告查看 |
| **规则引擎** | P1 | 预设规则（Java安全/性能/规范）+ 用户自定义prompt |
| **审查报告导出** | P1 | PDF/JSON格式，含问题位置、严重程度、修复建议 |
| **多文件批量审查** | P1 | 支持一次审查整个PR/分支的所有变更 |
| **CI/CD集成** | P2 | Git Webhook触发自动审查，结果推送到飞书/钉钉 |
| **IDE插件** | P3 | VSCode插件，实时审查当前文件 |

### 2.3 MVP不做（后续版本）

- 多语言支持（先只支持Java，后续加Python/Go/JS）
- 自动修复（只给建议，不改代码）
- 团队协作/权限管理（P2版本再加）
- 历史趋势分析

---

## 三、技术架构

### 3.1 总体架构

```
┌─────────────────────────────────────────────────────────────┐
│                     用户浏览器                                │
│              Vue3 + Element Plus 管理台                     │
└─────────────────────────────────────────────────────────────┘
                              ↑↓ REST API :8080
┌─────────────────────────────────────────────────────────────┐
│                 CodeAudit 后端 (Spring Boot)                │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐      │
│  │ Project  │  │ Review   │  │ Git      │  │ Report   │      │
│  │ Service  │  │ Service   │  │ Service  │  │ Service  │      │
│  │ 项目管理  │  │ 审查核心  │  │ 代码提取  │  │ 报告生成  │      │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘      │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │ Rule Engine（规则引擎）                              │    │
│  │  - 内置规则：Java安全/性能/规范                       │    │
│  │  - 自定义Prompt规则（用户可编辑）                    │    │
│  └────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                  │
│  │ MySQL 8   │  │ Ollama    │  │ File      │                  │
│  │ (项目/报告)│  │ :11434   │  │ Storage   │                  │
│  └──────────┘  └──────────┘  └──────────┘                  │
└─────────────────────────────────────────────────────────────┘
                              ↑
┌─────────────────────────────────────────────────────────────┐
│                    N100 小主机（24h运行）                     │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| 前端 | Vue3 + Element Plus + Vite | 管理台界面，复用HomeGallery前端经验 |
| 后端 | Spring Boot 3.2 + Spring AI | 复用HomeGallery基础设施 |
| AI引擎 | Ollama + Qwen3:8b (本地) | 代码审查不需要大模型，8B够用 |
| 数据库 | MySQL 8 | 复用HomeGallery的MySQL实例 |
| Git操作 | JGit (Java) 或 Runtime.exec(git) | 提取diff、commit历史 |
| 报告生成 | OpenPDF / Flying Saucer | HTML转PDF |
| 部署 | Docker Compose | 复用CI/CD方案 |

---

## 四、核心模块设计

### 4.1 Git代码提取模块

```java
@Service
public class GitDiffService {
    
    /**
     * 提取指定commit的diff
     */
    public List<DiffBlock> extractDiff(String repoPath, String commitId) {
        // 使用JGit或命令行
        // git diff HEAD~1 HEAD -- .java
        // 返回：文件路径 + 变更类型(ADD/MODIFY/DELETE) + diff内容
    }
    
    /**
     * 提取两个分支之间的所有diff
     */
    public List<DiffBlock> extractDiffBetweenRefs(String repoPath, String fromRef, String toRef) {
        // git diff fromRef..toRef -- .java
    }
}

public record DiffBlock(
    String filePath,      // src/main/java/.../UserService.java
    String changeType,    // ADD / MODIFY / DELETE
    int addedLines,       // 新增行数
    int removedLines,     // 删除行数
    String diffContent,   // @@ -1,5 +1,8 @@ ...
    String fullContent    // 变更后完整文件内容（用于上下文分析）
) {}
```

### 4.2 AI审查引擎

```java
@Service
public class ReviewEngine {
    
    @Autowired private ChatClient chatClient; // Spring AI Ollama适配器
    @Autowired private RuleRepository ruleRepo;
    
    public ReviewResult review(DiffBlock diff, List<Rule> rules) {
        // 1. 构建Prompt
        String prompt = buildReviewPrompt(diff, rules);
        
        // 2. 调本地Ollama
        String response = chatClient.prompt(prompt).call().content();
        
        // 3. 解析LLM返回的结构化结果
        return parseReviewResponse(response);
    }
    
    private String buildReviewPrompt(DiffBlock diff, List<Rule> rules) {
        return """
            你是一名资深Java代码审查专家。请审查以下代码变更，找出安全漏洞、性能问题、代码规范违规。
            
            审查规则：
            %s
            
            代码文件：%s
            变更类型：%s
            
            变更代码：
            ```java
            %s
            ```
            
            请按以下JSON格式返回审查结果：
            {
              "issues": [
                {
                  "severity": "HIGH|MEDIUM|LOW",
                  "category": "SECURITY|PERFORMANCE|STYLE|BUG",
                  "line": 行号,
                  "message": "问题描述",
                  "suggestion": "修复建议"
                }
              ]
            }
            """.formatted(
                rules.stream().map(Rule::getPrompt).collect(Collectors.joining("\n")),
                diff.filePath(),
                diff.changeType(),
                diff.diffContent()
            );
    }
}
```

### 4.3 规则引擎设计

```java
@Entity
public class Rule {
    @Id
    private Long id;
    private String name;           // "SQL注入检查"
    private String category;       // SECURITY / PERFORMANCE / STYLE / BUG
    private String language;       // java / python / go
    private String prompt;         // 给LLM的审查指令
    private boolean enabled;       // 是否启用
    private boolean builtin;       // 是否内置（不可删除）
}

// 内置规则示例
public static final String SQL_INJECTION_RULE = """
    - 检查是否存在SQL拼接（String + 变量直接拼SQL）
    - 检查MyBatis是否有#{}参数化，而不是${}直接替换
    - 检查是否有预编译语句缺失的情况
""";

public static final String NPE_RULE = """
    - 检查方法参数是否有null检查
    - 检查Optional/Stream操作是否有空值风险
    - 检查getter返回值是否可能为null且未处理
""";

public static final String PERFORMANCE_RULE = """
    - 检查循环中是否有数据库查询（N+1问题）
    - 检查String拼接是否用了+（应改用StringBuilder）
    - 检查是否有重复创建对象（如SimpleDateFormat）
""";
```

---

## 五、数据库表结构

```sql
-- 项目表
CREATE TABLE ca_projects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL COMMENT '项目名称',
    repo_path VARCHAR(500) NOT NULL COMMENT 'Git仓库本地路径',
    default_branch VARCHAR(64) DEFAULT 'main',
    language VARCHAR(32) DEFAULT 'java' COMMENT '主要语言',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 审查任务表
CREATE TABLE ca_reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    title VARCHAR(255) COMMENT '审查标题（如：PR #42 审查）',
    source_type ENUM('manual','git_hook','scheduled') DEFAULT 'manual',
    from_commit VARCHAR(40) COMMENT '起始commit',
    to_commit VARCHAR(40) COMMENT '目标commit',
    status ENUM('pending','processing','completed','failed') DEFAULT 'pending',
    issue_count INT DEFAULT 0,
    high_count INT DEFAULT 0,
    medium_count INT DEFAULT 0,
    low_count INT DEFAULT 0,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES ca_projects(id),
    INDEX idx_project (project_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 审查结果表（问题列表）
CREATE TABLE ca_issues (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    review_id BIGINT NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    line_number INT COMMENT '问题所在行号',
    severity ENUM('HIGH','MEDIUM','LOW') NOT NULL,
    category ENUM('SECURITY','PERFORMANCE','STYLE','BUG','OTHER') NOT NULL,
    rule_name VARCHAR(128) COMMENT '命中规则名',
    message TEXT NOT NULL COMMENT '问题描述',
    suggestion TEXT COMMENT '修复建议',
    code_snippet TEXT COMMENT '相关代码片段',
    status ENUM('open','resolved','ignored') DEFAULT 'open',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (review_id) REFERENCES ca_reviews(id) ON DELETE CASCADE,
    INDEX idx_review (review_id),
    INDEX idx_severity (severity),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 规则表
CREATE TABLE ca_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    category ENUM('SECURITY','PERFORMANCE','STYLE','BUG','OTHER') NOT NULL,
    language VARCHAR(32) DEFAULT 'java',
    prompt TEXT NOT NULL COMMENT 'LLM审查指令',
    description VARCHAR(500),
    enabled TINYINT DEFAULT 1,
    builtin TINYINT DEFAULT 0 COMMENT '1=内置不可删',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_category (category),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 插入内置规则
INSERT INTO ca_rules (name, category, language, prompt, description, builtin) VALUES
('SQL注入检查', 'SECURITY', 'java', 
 '检查是否存在SQL拼接（String + 变量直接拼SQL）。检查MyBatis是否有#{}参数化。检查预编译语句缺失。',
 '检测SQL注入风险', 1),
('空指针检查', 'BUG', 'java',
 '检查方法参数是否有null检查。检查Optional/Stream操作的空值风险。检查getter返回值未处理的情况。',
 '检测NPE风险', 1),
('性能优化', 'PERFORMANCE', 'java',
 '检查循环中是否有数据库查询（N+1问题）。检查String拼接是否用了+。检查是否有重复创建对象。',
 '检测性能问题', 1),
('资源泄漏', 'BUG', 'java',
 '检查流、连接、锁是否正确关闭（try-with-resources）。检查是否有死锁风险。',
 '检测资源泄漏', 1);
```

---

## 六、前端界面原型

### 6.1 页面列表

| 页面 | 说明 |
|------|------|
| /projects | 项目列表（增删改查） |
| /projects/{id}/reviews | 项目审查历史列表 |
| /reviews/{id} | 审查报告详情（问题列表+代码高亮） |
| /rules | 规则管理（内置+自定义） |
| /settings | 系统设置（Ollama地址、模型选择） |

### 6.2 审查报告详情页

```
┌────────────────────────────────────────────────────────────────┐
│ CodeAudit                                      [新建审查]       │
├────────────────────────────────────────────────────────────────┤
│ 项目：HomeGallery    审查：PR #42 → main                        │
│ 状态：✅ 已完成      耗时：3分12秒                              │
│                                                                │
│ 高危 2  │ 中危 5  │ 低危 8                                      │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━   │
│                                                                │
│ 🔴 [高危] SQL注入风险                                            │
│    文件：src/main/java/UserMapper.java:23                        │
│    问题：使用${}直接拼接SQL参数，存在SQL注入风险                   │
│    建议：改为#{}预编译参数化                                     │
│    [查看代码] [标记已修复]                                        │
│                                                                │
│ 🟡 [中危] 空指针风险                                             │
│    文件：src/main/java/OrderService.java:45                     │
│    问题：user.getName()可能返回null，直接调用toUpperCase()会NPE   │
│    建议：增加null检查或使用Optional                              │
│    [查看代码] [标记已修复]                                        │
│                                                                │
│ ...更多问题...                                                   │
└────────────────────────────────────────────────────────────────┘
```

---

## 七、部署方案（N100）

```yaml
# /opt/codeaudit/docker-compose.yml
version: "3.8"

services:
  codeaudit:
    build: ./codeaudit
    container_name: codeaudit
    restart: unless-stopped
    environment:
      DB_HOST: mysql
      OLLAMA_BASE_URL: http://PC-IP:11434
    volumes:
      - /home/codeaudit/repos:/repos:ro    # 挂载本地Git仓库
    ports:
      - "8090:8080"
    networks:
      - hg-net  # 复用HomeGallery网络

  # 复用HomeGallery已有的MySQL，不用新建
  # MySQL需要执行上面的建表SQL
```

---

## 八、实施路线图

### 第1周：基础搭建
- [ ] Day 1-2：后端项目初始化（Spring Boot + Spring AI + MySQL连接）
- [ ] Day 3-4：Git diff提取模块（JGit集成，diff解析）
- [ ] Day 5-7：AI审查引擎（Prompt设计 + Ollama调用 + 结果解析）

### 第2周：Web界面
- [ ] Day 8-10：Vue3前端项目初始化 + Element Plus
- [ ] Day 11-12：项目列表/新建/删除页面
- [ ] Day 13-14：审查报告列表 + 报告详情页面

### 第3周：核心功能闭环
- [ ] Day 15-17：审查任务触发（手动触发 + 异步执行）
- [ ] Day 18-19：报告导出（PDF/JSON）
- [ ] Day 20-21：规则管理页面（内置规则展示）

### 第4周：规则引擎 + 优化
- [ ] Day 22-24：规则引擎（自定义Prompt规则CRUD）
- [ ] Day 25-26：前端代码高亮（diff展示）
- [ ] Day 27-28：Docker打包 + N100部署测试

### 第5-6周：打磨 + 开源准备
- [ ] 多文件批量审查
- [ ] 审查结果准确率验证（找真实Java项目测试）
- [ ] README + 开源许可证选择（MIT/Apache 2.0）
- [ ] GitHub仓库创建，发布v0.1.0

---

## 九、开源策略

### 开源范围
- **全部开源**：后端代码 + 前端代码 + Docker部署
- **免费使用**：个人开发者、小团队（<5人）

### 商业版增值功能
- 团队协作（多用户、权限管理）
- CI/CD集成（Git Webhook、飞书/钉钉推送）
- 自定义规则高级版（正则匹配 + AI规则组合）
- 企业支持（邮件/微信技术支持）

---

> MVP目标：4-6周出一个"导入Git仓库 → 手动触发审查 → 查看AI报告"的完整闭环。准确率不是第一优先，能跑通流程、能给出合理建议是第一步。
