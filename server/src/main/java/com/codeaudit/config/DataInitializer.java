package com.codeaudit.config;

import com.codeaudit.entity.User;
import com.codeaudit.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 数据初始化器 — 启动时自动创建默认管理员账号
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public DataInitializer(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        if (!userRepository.existsByUsername("admin")) {
            User admin = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin"))
                    .displayName("管理员")
                    .email("admin@codeaudit.local")
                    .role("ADMIN")
                    .enabled(true)
                    .build();
            userRepository.save(admin);
            log.info("默认管理员账号已创建: admin / admin");
        }
    }
}
