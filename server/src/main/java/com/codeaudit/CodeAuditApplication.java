package com.codeaudit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * CodeAudit 应用主入口
 * <p>
 * 本地 AI 代码审查平台，基于 Spring Boot 3.2 构建。
 * 核心能力：接入本地 Git 仓库 → 提取 diff → 本地 Ollama AI 审查 → Web 展示报告。
 *
 * @author CodeAudit Team
 */
@SpringBootApplication
@EnableJpaAuditing
public class CodeAuditApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeAuditApplication.class, args);
    }
}
