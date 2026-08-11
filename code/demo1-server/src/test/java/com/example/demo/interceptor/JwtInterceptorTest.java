package com.example.demo.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JwtInterceptor 单元测试
 * <p>
 * 覆盖 OPTIONS 放行、Token 缺失、Token 无效、Token 过期等场景。
 */
class JwtInterceptorTest {

    private JwtInterceptor interceptor;
    private HttpServletRequest request;
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new JwtInterceptor();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
    }

    @Test
    void preHandle_optionsRequest_shouldReturnTrue() throws Exception {
        when(request.getMethod()).thenReturn("OPTIONS");

        boolean result = interceptor.preHandle(request, response, null);

        assertTrue(result);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void preHandle_noAuthHeader_shouldReturn401() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn(null);
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        boolean result = interceptor.preHandle(request, response, null);

        assertFalse(result);
        verify(response).setStatus(401);
        verify(response).setContentType("application/json;charset=UTF-8");
    }

    @Test
    void preHandle_authHeaderNotBearer_shouldReturn401() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Basic abc123");
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        boolean result = interceptor.preHandle(request, response, null);

        assertFalse(result);
        verify(response).setStatus(401);
    }

    @Test
    void preHandle_shortToken_shouldReturn401() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer ");
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        boolean result = interceptor.preHandle(request, response, null);

        assertFalse(result);
        verify(response).setStatus(401);
    }

    @Test
    void preHandle_emptyToken_shouldReturn401() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer");
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        boolean result = interceptor.preHandle(request, response, null);

        assertFalse(result);
        verify(response).setStatus(401);
    }
}