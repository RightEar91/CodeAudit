package com.codeaudit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 应用启动测试
 * <p>
 * 使用 H2 内存数据库验证 Spring 上下文正常加载。
 *
 * @author CodeAudit Team
 */
@SpringBootTest
@Import(TestAiChatConfig.class)
class CodeAuditApplicationTests {

    @Test
    void contextLoads() {
    }
}
