package com.codeaudit.service;

import com.codeaudit.common.BizException;
import com.codeaudit.common.JwtUtils;
import com.codeaudit.dto.LoginRequest;
import com.codeaudit.dto.LoginResponse;
import com.codeaudit.dto.RegisterRequest;
import com.codeaudit.entity.User;
import com.codeaudit.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务 — 登录、注册、Token 管理
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository, JwtUtils jwtUtils) {
        this.userRepository = userRepository;
        this.jwtUtils = jwtUtils;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BizException("用户名或密码错误"));

        if (!user.getEnabled()) {
            throw new BizException("账号已被禁用");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException("用户名或密码错误");
        }

        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole());
        log.info("用户登录成功: {}", user.getUsername());

        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .build();
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BizException("用户名已存在");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName() != null ? request.getDisplayName() : request.getUsername())
                .email(request.getEmail())
                .role("DEVELOPER")
                .enabled(true)
                .build();

        user = userRepository.save(user);
        log.info("新用户注册: {}", user.getUsername());

        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole());

        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .build();
    }

    public LoginResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(404, "用户不存在"));
        return LoginResponse.builder()
                .token(null)
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .build();
    }
}
