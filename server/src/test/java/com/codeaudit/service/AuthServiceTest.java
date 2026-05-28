package com.codeaudit.service;

import com.codeaudit.common.BizException;
import com.codeaudit.common.JwtUtils;
import com.codeaudit.dto.LoginRequest;
import com.codeaudit.dto.LoginResponse;
import com.codeaudit.dto.RegisterRequest;
import com.codeaudit.entity.User;
import com.codeaudit.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    private final JwtUtils jwtUtils = new JwtUtils(
            "Test-Secret-Must-Be-At-Least-256-Bits-Long-Enough-HS256-HMAC",
            3600000);

    private AuthService authService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, jwtUtils);
    }

    @Test
    void login_shouldReturnToken_whenCredentialsValid() {
        String rawPassword = "admin123";
        User user = User.builder()
                .id(1L).username("admin")
                .password(encoder.encode(rawPassword))
                .displayName("管理员").role("ADMIN").enabled(true)
                .build();

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword(rawPassword);

        LoginResponse response = authService.login(request);

        assertNotNull(response.getToken());
        assertEquals(1L, response.getUserId());
        assertEquals("admin", response.getUsername());
        assertEquals("ADMIN", response.getRole());
        assertEquals("管理员", response.getDisplayName());
    }

    @Test
    void login_shouldThrow_whenUsernameNotFound() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest();
        request.setUsername("nobody");
        request.setPassword("anything");

        BizException ex = assertThrows(BizException.class, () -> authService.login(request));
        assertEquals("用户名或密码错误", ex.getMessage());
    }

    @Test
    void login_shouldThrow_whenPasswordWrong() {
        User user = User.builder()
                .id(1L).username("admin")
                .password(encoder.encode("correct"))
                .role("ADMIN").enabled(true)
                .build();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("wrong");

        BizException ex = assertThrows(BizException.class, () -> authService.login(request));
        assertEquals("用户名或密码错误", ex.getMessage());
    }

    @Test
    void login_shouldThrow_whenUserDisabled() {
        User user = User.builder()
                .id(1L).username("admin")
                .password(encoder.encode("admin"))
                .role("ADMIN").enabled(false)
                .build();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("admin");

        BizException ex = assertThrows(BizException.class, () -> authService.login(request));
        assertEquals("账号已被禁用", ex.getMessage());
    }

    @Test
    void register_shouldCreateUserAndReturnToken() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        User saved = User.builder().id(3L).username("newuser")
                .password("encoded").displayName("newuser").role("DEVELOPER").enabled(true).build();
        when(userRepository.save(captor.capture())).thenReturn(saved);

        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("secret123");

        LoginResponse response = authService.register(request);

        assertNotNull(response.getToken());
        assertEquals(3L, response.getUserId());
        assertEquals("newuser", response.getUsername());
        assertEquals("DEVELOPER", response.getRole());

        User captured = captor.getValue();
        assertEquals("newuser", captured.getUsername());
        assertNotEquals("secret123", captured.getPassword());
        assertTrue(encoder.matches("secret123", captured.getPassword()));
    }

    @Test
    void register_shouldThrow_whenUsernameExists() {
        when(userRepository.existsByUsername("taken")).thenReturn(true);

        RegisterRequest request = new RegisterRequest();
        request.setUsername("taken");
        request.setPassword("anything");

        BizException ex = assertThrows(BizException.class, () -> authService.register(request));
        assertEquals("用户名已存在", ex.getMessage());
    }

    @Test
    void register_shouldUseDisplayName_whenProvided() {
        when(userRepository.existsByUsername("user")).thenReturn(false);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        User saved = User.builder().id(4L).username("user")
                .displayName("MyName").role("DEVELOPER").enabled(true).build();
        when(userRepository.save(captor.capture())).thenReturn(saved);

        RegisterRequest request = new RegisterRequest();
        request.setUsername("user");
        request.setPassword("pass123");
        request.setDisplayName("MyName");

        authService.register(request);

        assertEquals("MyName", captor.getValue().getDisplayName());
    }

    @Test
    void getCurrentUser_shouldReturnUserInfo() {
        User user = User.builder().id(1L).username("admin")
                .displayName("管理员").role("ADMIN").enabled(true).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        LoginResponse response = authService.getCurrentUser(1L);

        assertNull(response.getToken());
        assertEquals(1L, response.getUserId());
        assertEquals("admin", response.getUsername());
    }

    @Test
    void getCurrentUser_shouldThrow_whenUserNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> authService.getCurrentUser(999L));
        assertEquals(404, ex.getCode());
    }
}
