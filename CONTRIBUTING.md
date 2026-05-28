# Contributing to CodeAudit

Thanks for your interest in contributing! Here's how to get started.

## Table of Contents

- [Development Setup](#development-setup)
- [Project Structure](#project-structure)
- [Development Workflow](#development-workflow)
- [Code Style](#code-style)
- [Testing](#testing)
- [Pull Request Process](#pull-request-process)
- [Issue Guidelines](#issue-guidelines)

---

## Development Setup

### Prerequisites

- **JDK** 17+
- **Maven** 3.8+
- **Node.js** 18+
- **MySQL** 8.0+
- **Ollama** (for local AI model testing)

### Quick Start

```bash
# 1. Clone and install
git clone https://github.com/<your-org>/code-aduit.git
cd code-aduit

# 2. Create a MySQL database
mysql -u root -e "CREATE DATABASE codeaudit CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 3. Start backend
cd server
# Copy and edit local config
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 4. Start frontend (in another terminal)
cd web
npm install
npm run dev
```

Visit `http://localhost:5173` and log in with `admin` / `admin`.

### Docker Setup

```bash
cp .env.example .env
docker compose up -d
```

---

## Project Structure

```
code-aduit/
├── server/                          # Spring Boot backend (Java 17)
│   └── src/
│       ├── main/java/com/codeaudit/
│       │   ├── common/              # Utilities (Response, BizException, JwtUtils)
│       │   ├── config/              # Spring configs (Security, Async, Jackson)
│       │   ├── controller/          # REST controllers
│       │   ├── dto/                 # Data transfer objects (request / response)
│       │   ├── entity/              # JPA entities
│       │   ├── repository/          # Spring Data repositories
│       │   └── service/             # Business logic
│       └── resources/
│           ├── application.yml      # Main config (env-var placeholders)
│           └── db/
├── web/                             # Vue 3 frontend (TypeScript)
│   └── src/
│       ├── api/                     # Axios API client
│       ├── components/              # Shared components
│       ├── composables/             # Vue composables
│       ├── router/                  # Vue Router config
│       └── views/                   # Page components
├── docs/
│   └── images/                      # Screenshots for README
└── docker-compose.yml               # Docker deployment
```

---

## Development Workflow

### Backend

```bash
cd server
# Clean compile
mvn compile

# Run tests
mvn test

# Run all tests except slow integration tests
mvn test -Dtest="!OpenAiAiChatServiceTest"

# Run a single test class
mvn test -Dtest="AuthServiceTest"
```

### Frontend

```bash
cd web
# Type check
npx vue-tsc --noEmit

# Lint
npm run lint

# Build for production
npm run build
```

---

## Code Style

### Backend (Java)

- Use **Lombok** annotations (`@Data`, `@Builder`, `@Getter`) where appropriate.
- **Constructor injection** over `@Autowired` field injection.
- Controllers return `Response<T>` (unified response wrapper).
- DTOs and VO are separated: request DTOs in `dto/`, entity objects are NOT exposed to API.
- Use `BizException` for business errors — never return raw `500`.
- Add **Javadoc** on public classes and methods following existing patterns.
- Chinese docstrings are welcome (matching existing style).

### Frontend (TypeScript / Vue 3)

- Use `<script setup lang="ts">` with Composition API.
- API calls go through `@/api/index.ts` (Axios instance with interceptors).
- Use `ElMessage` from Element Plus for user feedback.
- CSS uses CSS custom properties from `global.css` — no hardcoded colors.
- Component files: PascalCase (e.g. `AppLayout.vue`).

---

## Testing

- **Unit tests** go in `server/src/test/`. Name: `<ClassUnderTest>Test.java`.
- **Integration tests** use `@SpringBootTest` + `@Import(TestAiChatConfig.class)` + `MockMvc`.
- Mock AI services with `TestAiChatConfig` (provides `ChatModel` mock + `@Primary AiChatService`).
- Test database: H2 in-memory, auto-applied via `test/resources/application.yml`.
- Frontend tests: not yet configured — contributions welcome!

```bash
# All tests should pass before submitting a PR
cd server
mvn test -Dtest="!OpenAiAiChatServiceTest"
# Expected: BUILD SUCCESS, 88+ tests, 0 failures
```

---

## Pull Request Process

1. **Fork** the repo and create your branch from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Write code** following the [Code Style](#code-style) conventions.

3. **Add tests** — new features should include unit and/or integration tests.

4. **Run all tests** and ensure they pass:
   ```bash
   cd server && mvn test -Dtest="!OpenAiAiChatServiceTest"
   ```

5. **Run type check** for frontend:
   ```bash
   cd web && npx vue-tsc --noEmit
   ```

6. **Update documentation** if your change affects:
   - API endpoints → update README API reference
   - Configuration → update `.env.example` / `application-local.yml.example`
   - User-facing behavior → update README usage guide

7. **Commit** with a descriptive message:
   ```
   feat: add multi-model review comparison
   fix: resolve sidebar not appearing after login
   docs: add screenshots to README
   test: add AuthController integration tests
   ```

8. **Push** and open a Pull Request against `main`.

### PR Checklist

- [ ] Code follows project conventions
- [ ] Tests added and passing
- [ ] No hardcoded credentials or secrets
- [ ] Documentation updated if needed
- [ ] Frontend type check passes

---

## Issue Guidelines

### Bug Reports

Include:
- **Environment**: OS, JDK/Node version, Docker or source build
- **Steps to reproduce**: minimal reproduction steps
- **Expected vs actual behavior**
- **Logs**: server logs and browser console errors

### Feature Requests

- Describe the problem you're solving, not just the solution.
- Link to existing issues if related.
- If you're willing to implement it, mention it!

---

## Code of Conduct

This project follows the [Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md). By participating you agree to abide by its terms.

---

## Questions?

Feel free to open an issue or start a discussion. We're happy to help!
