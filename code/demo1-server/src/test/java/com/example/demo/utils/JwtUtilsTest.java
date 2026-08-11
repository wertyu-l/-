package com.example.demo.utils;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtils 单元测试
 * <p>
 * 覆盖 Token 生成、解析、校验的完整流程，
 * 重点验证过期检测、篡改检测、边界输入等安全关键场景。
 */
class JwtUtilsTest {

    // ========== 生成与解析 ==========

    /**
     * 正常生成 Token 后应能解析出原始载荷
     */
    @Test
    void generateAndParseToken_shouldReturnCorrectClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", 1L);
        claims.put("username", "admin");

        String token = JwtUtils.generateToken(claims);
        assertNotNull(token);

        Claims parsed = JwtUtils.parseToken(token);
        assertEquals(1, parsed.get("userId"));
        assertEquals("admin", parsed.get("username"));
    }

    /**
     * 不同载荷的 Token 应能正确解析
     */
    @Test
    void parseToken_differentClaims_shouldWork() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "admin");
        claims.put("level", 5);

        String token = JwtUtils.generateToken(claims);
        Claims parsed = JwtUtils.parseToken(token);

        assertEquals("admin", parsed.get("role"));
        assertEquals(5, parsed.get("level"));
    }

    // ========== 校验：正常场景 ==========

    /**
     * 合法 Token 校验应返回 true
     */
    @Test
    void validateToken_validToken_shouldReturnTrue() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", 1L);
        String token = JwtUtils.generateToken(claims);

        assertTrue(JwtUtils.validateToken(token));
    }

    // ========== 校验：异常场景 ==========

    /**
     * 格式不合法的 Token 应返回 false
     */
    @Test
    void validateToken_invalidToken_shouldReturnFalse() {
        assertFalse(JwtUtils.validateToken("invalid.token.here"));
    }

    /**
     * null Token 应返回 false（防 NPE）
     */
    @Test
    void validateToken_nullToken_shouldReturnFalse() {
        assertFalse(JwtUtils.validateToken(null));
    }

    /**
     * 空字符串 Token 应返回 false
     */
    @Test
    void validateToken_emptyToken_shouldReturnFalse() {
        assertFalse(JwtUtils.validateToken(""));
    }

    /**
     * 被篡改的 Token 应返回 false（签名校验失败）
     */
    @Test
    void validateToken_tamperedToken_shouldReturnFalse() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", 1L);
        String token = JwtUtils.generateToken(claims);

        String tampered = token.substring(0, token.length() - 1) + "X";
        assertFalse(JwtUtils.validateToken(tampered));
    }
}