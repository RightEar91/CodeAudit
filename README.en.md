# CodeAudit — Local AI Code Review Platform

A **fully local** AI code review tool. Your source code never leaves your network — AI analysis runs via local Ollama or cloud OpenAI-compatible APIs, with results displayed in a web management console.

> **Current version**: v0.1.0 (MVP)

---

## ✨ Core Features

| Module | Description |
|--------|-------------|
| 📦 Git Integration | Scan local Git repos, extract branch-to-branch / commit-to-commit diffs |
| 🔍 AI Code Review | Feed diffs file-by-file to LLM, return structured issues (HIGH/MEDIUM/LOW) |
| 📊 Web Console | Project CRUD, review history, report details, issue status management |
| 📏 Rule Engine | Built-in presets + custom Prompt rules CRUD + enable/disable |
| 🎨 Diff Preview | View changed files list, full diff content with syntax highlighting |
| 🌐 Multi-language | Java / Python / Go / JavaScript / TypeScript / C / C++ and more |
| 🤖 Multi-model | Local Ollama + cloud OpenAI-compatible API (DeepSeek / Alibaba Bailian etc.) |
| ⚡ Parallel Review | Multi-file concurrent AI calls, configurable parallelism (1–8) |
| 🎯 Issue Tracking | Mark issues as 「Resolved / Ignored」|

---

## 🏗️ Tech Stack

### Backend

| Technology | Version | Purpose |
|------------|---------|---------|
| Spring Boot | 3.2.5 | Application framework |
| Spring AI | 1.0.0-M6 | Ollama / OpenAI integration |
| JPA (Hibernate) | — | Data persistence |
| JGit | 6.9.0 | Git repository operations |
| MySQL | 8.0+ | Data storage |

### Frontend

| Technology | Version | Purpose |
|------------|---------|---------|
| Vue 3 | 3.5 | Frontend framework |
| Element Plus | 2.14 | UI component library |
| highlight.js | 11.11 | Code syntax highlighting |
| Axios | 1.16 | HTTP client |
| TypeScript | 6.0 | Type safety |

---

## 🚀 Quick Start

### Requirements

- **JDK** 17+
- **Maven** 3.8+
- **Node.js** 18+
- **MySQL** 8.0+
- **Ollama** (for local models) or OpenAI-compatible API key

### 1. Clone the repo

```bash
git clone <your-repo-url>
cd code-aduit
```

### 2. Start Ollama (optional, for local models)

```bash
ollama serve
ollama pull qwen3:8b
```

### 3. Configure environment variables

Create a `.env` file or set system environment variables:

```env
# MySQL
MYSQL_URL=jdbc:mysql://localhost:3306/codeaudit?useSSL=false&serverTimezone=Asia/Shanghai
MYSQL_USERNAME=root
MYSQL_PASSWORD=your_password

# Ollama (for local models)
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=qwen3:8b
OLLAMA_TEMPERATURE=0.1
OLLAMA_TOP_P=0.9

# OpenAI (for cloud APIs, optional)
OPENAI_API_KEY=sk-xxxxx
OPENAI_BASE_URL=https://api.openai.com
OPENAI_MODEL=gpt-4o
```

### 4. Start the backend

```bash
cd server
mvn spring-boot:run
```

Backend runs on `http://localhost:9090` by default.

### 5. Start the frontend

```bash
cd web
npm install
npm run dev
```

Frontend runs on `http://localhost:5173` by default, with proxy configured to forward API requests to the backend.

---

## 📖 Usage Guide

### 1. Add a project

Go to the 「Projects」 page, click 「Add Project」, and fill in:
- **Project Name**: any display name
- **Repo Path**: absolute path to a local Git repo (e.g. `/home/user/my-app`)
- **Language**: Java / Python / Go etc.

### 2. Create a review

Go to the 「Reviews」 page, click 「New Review」:
- Select a project and branch / commit range
- Click 「Preview Changes」 to see the files to be reviewed
- Submit the review — the system will run AI analysis asynchronously in the background

### 3. View results

After review completes, click on the review record to see details:
- Browse all issues (graded as HIGH / MEDIUM / LOW)
- Click on an issue to view details and fix suggestions
- Mark issue status (Resolved / Ignored)

### 4. Manage rules

Go to the 「Rules」 page:
- View and manage the rules used by AI reviews
- Add custom rules (name, category, language, prompt description)
- Enable / disable rules

### 5. System settings

Go to the 「Settings」 page:
- Switch AI provider: local Ollama / cloud OpenAI-compatible API
- Model selection: auto-fetch installed models from Ollama
- Configure parallelism, temperature, and other parameters
- Test Ollama connection

---

## 📁 Project Structure

```
code-aduit/
├── server/                          # Spring Boot backend
│   └── src/
│       ├── main/java/com/codeaudit/
│       │   ├── common/              # Common utilities (Response, BizException)
│       │   ├── config/              # Configuration (Async, GlobalException, Web)
│       │   ├── controller/          # REST controllers
│       │   ├── dto/                 # Data transfer objects
│       │   ├── entity/              # JPA entities (Project, Review, Issue, Rule)
│       │   ├── repository/          # Data access layer
│       │   └── service/             # Business logic layer
│       └── resources/
│           ├── application.yml      # Main configuration
│           └── db/data.sql          # Seed data
├── web/                             # Vue 3 frontend
│   └── src/
│       ├── api/                     # API wrappers
│       ├── components/              # Shared components
│       ├── router/                  # Route configuration
│       ├── utils/                   # Utility functions
│       └── views/                   # Pages
│           ├── Projects.vue         # Project management
│           ├── Reviews.vue          # Review history + new review
│           ├── ReviewDetail.vue     # Review detail
│           ├── Rules.vue            # Rule management
│           └── Settings.vue         # System settings
└── doc/                             # Design documents
```

---

## 🔌 API Overview

### Project Management

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/projects` | List projects (search & pagination) |
| `POST` | `/api/projects` | Create a project |
| `GET` | `/api/projects/{id}` | Project details |
| `PUT` | `/api/projects/{id}` | Update a project |
| `DELETE` | `/api/projects/{id}` | Delete a project (cascading reviews) |
| `GET` | `/api/projects/{id}/branches` | List branches |
| `GET` | `/api/projects/{id}/commits` | List recent commits |
| `GET` | `/api/projects/{id}/diff-preview` | Preview changed files |

### Review Management

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/projects/{projectId}/reviews` | Review history (paginated) |
| `POST` | `/api/projects/{projectId}/reviews` | Create a review (async execution) |
| `GET` | `/api/reviews/{id}` | Review details |
| `POST` | `/api/reviews/{id}/cancel` | Cancel a review |
| `DELETE` | `/api/reviews/{id}` | Delete a review |
| `GET` | `/api/reviews/{reviewId}/issues` | Issue list (paginated) |
| `PUT` | `/api/issues/{id}/status` | Update issue status |

### Rule Management

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/rules` | List rules |
| `POST` | `/api/rules` | Create a rule |
| `PUT` | `/api/rules/{id}` | Update a rule |
| `PUT` | `/api/rules/{id}/toggle` | Enable / disable a rule |
| `DELETE` | `/api/rules/{id}` | Delete a rule |

### System Settings

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/settings` | Get current settings |
| `PUT` | `/api/settings` | Save settings |
| `GET` | `/api/ollama/check` | Test Ollama connection |
| `GET` | `/api/ollama/models` | Get installed model list |

---

## ⚙️ Configuration

The AI provider is controlled via `codeaudit.ai.provider`:

- `ollama` (default) — Uses local Ollama service, requires `spring.ai.ollama.*` config
- `openai` — Uses cloud OpenAI-compatible API, requires `codeaudit.ai.openai.*` config

You can switch providers at runtime on the Settings page. Changes take effect in the current session (revert to config file values on restart).

---

## 🧪 Development

### Backend tests

```bash
cd server
mvn test
```

### Frontend type check

```bash
cd web
npx vue-tsc --noEmit
```

---

## 📝 License

MIT
