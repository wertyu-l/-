package com.example.demo.config;

import com.example.demo.interceptor.JwtInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import static org.mockito.Mockito.*;

/**
 * WebConfig 单元测试
 * <p>
 * 验证 JWT 拦截器注册及路径配置。
 */
class WebConfigTest {

    @Test
    void addInterceptors_shouldRegisterJwtInterceptor() {
        WebConfig config = new WebConfig();
        InterceptorRegistry registry = mock(InterceptorRegistry.class);
        InterceptorRegistration registration = mock(InterceptorRegistration.class);

        when(registry.addInterceptor(any(JwtInterceptor.class))).thenReturn(registration);
        when(registration.addPathPatterns(anyString())).thenReturn(registration);

        config.addInterceptors(registry);

        verify(registry).addInterceptor(any(JwtInterceptor.class));
        verify(registration).addPathPatterns("/**");
    }
}