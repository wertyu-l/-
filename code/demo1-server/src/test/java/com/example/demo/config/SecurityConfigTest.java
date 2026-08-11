package com.example.demo.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SecurityConfig 单元测试
 * <p>
 * 验证 BCryptPasswordEncoder Bean 创建及加密/验证功能。
 */
class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void passwordEncoder_shouldReturnBCryptInstance() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        assertNotNull(encoder);
        assertTrue(encoder.getClass().getName().contains("BCrypt"));
    }

    @Test
    void passwordEncoder_shouldEncodeAndMatch() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        String raw = "myPassword123";
        String encoded = encoder.encode(raw);

        assertNotEquals(raw, encoded);
        assertTrue(encoder.matches(raw, encoded));
    }

    @Test
    void passwordEncoder_shouldRejectWrongPassword() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        String encoded = encoder.encode("correctPassword");

        assertFalse(encoder.matches("wrongPassword", encoded));
    }
}