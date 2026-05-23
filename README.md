# CodeAudit — 本地 AI 代码审查平台

一个**完全本地运行**的 AI 代码审查工具。源码不出内网，通过本地 Ollama 或云端 OpenAI 兼容 API 进行代码 diff 级审查，结果通过 Web 管理台展示。

> **当前版本**：v0.1.0（MVP）

---

## ✨ 核心功能

| 模块 | 功能 |
|------|------|
| 📦 Git 仓库接入 | 本地 Git 仓库路径扫描，提取分支间 / commit 间 diff |
| 🔍 AI 代码审查 | diff 逐文件送入 LLM，返回结构化问题列表（HIGH/MEDIUM/LOW） |
| 📊 Web 管理台 | 项目 CRUD、审查记录列表、报告详情、问题状态管理 |
| 📏 规则引擎 | 内置预设规则 + 用户自定义 Prompt 规则 CRUD + 启用/禁用 |
| 🎨 Diff 预览 | 变更文件列表查看、完整 diff 内容展示、语法高亮 |
| 🌐 多语言支持 | Java / Python / Go / JavaScript / TypeScript / C / C++ 等 |
| 🤖 多模型切换 | 本地 Ollama + 云端 OpenAI 兼容 API（DeepSeek / 阿里百炼等） |
| ⚡ 文件级并行审查 | 多文件并发调用 AI，并行度可配（1~8） |
| 🎯 问题追踪 | 问题可标记为「已修复 / 已忽略」|

---

## 🏗️ 技术栈

### 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.2.5 | 应用框架 |
| Spring AI | 1.0.0-M6 | Ollama / OpenAI 集成 |
| JPA (Hibernate) | — | 数据持久化 |
| JGit | 6.9.0 | Git 仓库操作 |
| MySQL | 8.0+ | 数据存储 |

### 前端

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue 3 | 3.5 | 前端框架 |
| Element Plus | 2.14 | UI 组件库 |
| highlight.js | 11.11 | 代码语法高亮 |
| Axios | 1.16 | HTTP 客户端 |
| TypeScript | 6.0 | 类型安全 |

---

## 🚀 快速开始

### 环境要求

- **JDK** 17+
- **Maven** 3.8+
- **Node.js** 18+
- **MySQL** 8.0+
- **Ollama**（本地模型）或 OpenAI 兼容 API Key

### 1. 克隆项目

```bash
git clone <your-repo-url>
cd code-aduit
```

### 2. 启动 Ollama（可选，使用本地模型时）

```bash
ollama serve
ollama pull qwen3:8b
```

### 3. 配置环境变量

创建 `.env` 或在系统环境变量中设置：

```env
# MySQL
MYSQL_URL=jdbc:mysql://localhost:3306/codeaudit?useSSL=false&serverTimezone=Asia/Shanghai
MYSQL_USERNAME=root
MYSQL_PASSWORD=your_password

# Ollama（使用本地模型时）
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=qwen3:8b
OLLAMA_TEMPERATURE=0.1
OLLAMA_TOP_P=0.9

# OpenAI（使用云端 API 时，可选）
OPENAI_API_KEY=sk-xxxxx
OPENAI_BASE_URL=https://api.openai.com
OPENAI_MODEL=gpt-4o
```

### 4. 启动后端

```bash
cd server
mvn spring-boot:run
```

后端默认运行在 `http://localhost:9090`

### 5. 启动前端

```bash
cd web
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`，已配置代理转发至后端。

---

## 📖 使用说明

### 1. 添加项目

进入「项目管理」页面，点击「添加项目」，填写：
- **项目名称**：任意显示名称
- **仓库路径**：本地 Git 仓库的绝对路径（如 `E:/projects/my-app`）
- **编程语言**：Java / Python / Go 等

### 2. 创建审查

进入「审查记录」页面，点击「新建审查」：
- 选择项目和分支 / commit 范围
- 点击「预览变更」查看待审查文件列表
- 提交审查任务，系统将在后台异步执行 AI 分析

### 3. 查看结果

审查完成后，点击审查记录进入详情页：
- 查看所有问题（HIGH / MEDIUM / LOW 分级）
- 点击问题查看详情、修复建议
- 标记问题处理状态（已修复 / 已忽略）

### 4. 规则管理

进入「审查规则」页面：
- 查看和管理 AI 审查使用的规则
- 添加自定义规则（名称、分类、语言、Prompt 描述）
- 启用 / 禁用规则

### 5. 系统设置

进入「系统设置」页面：
- 切换 AI 提供商：本地 Ollama / 云端 OpenAI 兼容 API
- 模型选择：自动拉取 Ollama 已安装模型列表
- 配置并行度、Temperature 等参数
- 检测 Ollama 连接状态

---

## 📁 项目结构

```
code-aduit/
├── server/                          # Spring Boot 后端
│   └── src/
│       ├── main/java/com/codeaudit/
│       │   ├── common/              # 通用类（Response、BizException）
│       │   ├── config/              # 配置（Async、GlobalException、Web）
│       │   ├── controller/          # REST 控制器
│       │   ├── dto/                 # 数据传输对象
│       │   ├── entity/              # JPA 实体（Project、Review、Issue、Rule）
│       │   ├── repository/          # 数据访问层
│       │   └── service/             # 业务逻辑层
│       └── resources/
│           ├── application.yml      # 主配置
│           └── db/data.sql          # 种子数据
├── web/                             # Vue 3 前端
│   └── src/
│       ├── api/                     # API 封装
│       ├── components/              # 公共组件
│       ├── router/                  # 路由配置
│       ├── utils/                   # 工具函数
│       └── views/                   # 页面
│           ├── Projects.vue         # 项目管理
│           ├── Reviews.vue          # 审查记录 + 新建审查
│           ├── ReviewDetail.vue     # 审查详情
│           ├── Rules.vue            # 规则管理
│           └── Settings.vue         # 系统设置
└── doc/                             # 设计文档
```

---

## 🔌 API 概览

### 项目管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/projects` | 项目列表（支持搜索、分页） |
| `POST` | `/api/projects` | 添加项目 |
| `GET` | `/api/projects/{id}` | 项目详情 |
| `PUT` | `/api/projects/{id}` | 更新项目 |
| `DELETE` | `/api/projects/{id}` | 删除项目（级联审查记录） |
| `GET` | `/api/projects/{id}/branches` | 分支列表 |
| `GET` | `/api/projects/{id}/commits` | 最近 commit 列表 |
| `GET` | `/api/projects/{id}/diff-preview` | 预览变更文件 |

### 审查管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/projects/{projectId}/reviews` | 审查历史（分页） |
| `POST` | `/api/projects/{projectId}/reviews` | 创建审查（异步执行） |
| `GET` | `/api/reviews/{id}` | 审查详情 |
| `POST` | `/api/reviews/{id}/cancel` | 取消审查 |
| `DELETE` | `/api/reviews/{id}` | 删除审查 |
| `GET` | `/api/reviews/{reviewId}/issues` | 问题列表（分页） |
| `PUT` | `/api/issues/{id}/status` | 更新问题状态 |

### 规则管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/rules` | 规则列表 |
| `POST` | `/api/rules` | 创建规则 |
| `PUT` | `/api/rules/{id}` | 更新规则 |
| `PUT` | `/api/rules/{id}/toggle` | 启用/禁用规则 |
| `DELETE` | `/api/rules/{id}` | 删除规则 |

### 系统设置

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/settings` | 获取当前配置 |
| `PUT` | `/api/settings` | 保存配置 |
| `GET` | `/api/ollama/check` | 检测 Ollama 连接 |
| `GET` | `/api/ollama/models` | 获取已安装模型列表 |

---

## ⚙️ 配置说明

AI 提供商通过 `codeaudit.ai.provider` 控制：

- `ollama`（默认）— 使用本地 Ollama 服务，需配置 `spring.ai.ollama.*`
- `openai` — 使用云端 OpenAI 兼容 API，需配置 `codeaudit.ai.openai.*`

运行时可在「系统设置」页面切换，当前会话生效（重启后还原为配置文件值）。

---

## 🧪 开发

### 后端测试

```bash
cd server
mvn test
```

### 前端类型检查

```bash
cd web
npx vue-tsc --noEmit
```

---

## 📝 License

MIT
