package com.codeaudit.controller;

import com.codeaudit.common.Response;
import com.codeaudit.dto.LoginRequest;
import com.codeaudit.dto.LoginResponse;
import com.codeaudit.dto.RegisterRequest;
import com.codeaudit.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 认证 REST 控制器 — 登录、注册、获取当前用户信息
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Response<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Response.ok(authService.login(request));
    }

    @PostMapping("/register")
    public Response<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Response.ok(authService.register(request));
    }

    @GetMapping("/me")
    public Response<LoginResponse> me(@RequestAttribute("userId") Long userId) {
        return Response.ok(authService.getCurrentUser(userId));
    }
}
