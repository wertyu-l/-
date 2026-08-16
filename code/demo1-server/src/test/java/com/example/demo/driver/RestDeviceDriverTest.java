package com.example.demo.driver;

import com.example.demo.common.Result;
import com.example.demo.model.SimDeviceCapability;
import com.example.demo.model.SimDeviceInfo;
import com.example.demo.model.SimDeviceStatus;
import com.example.demo.model.SimWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RestDeviceDriver 单元测试
 * <p>
 * 覆盖设备信息、能力、状态查询，窗口创建/更新/关闭/查询的完整流程，
 * 重点验证 HTTP 调用参数正确性、响应解析和错误处理。
 */
class RestDeviceDriverTest {

    private RestTemplate restTemplate;
    private RestDeviceDriver driver;
    private DeviceEndpoint endpoint;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        driver = new RestDeviceDriver();
        ReflectionTestUtils.setField(driver, "restTemplate", restTemplate);
        endpoint = new DeviceEndpoint();
        endpoint.setBaseUrl("http://192.168.1.100:8086");
    }

    // ========== getInfo ==========

    @Test
    void getInfo_success_shouldReturnDeviceInfo() {
        SimDeviceInfo info = new SimDeviceInfo();
        info.setDeviceName("TestDevice");
        Result<SimDeviceInfo> result = Result.success(info);
        ResponseEntity<Result<SimDeviceInfo>> response = ResponseEntity.ok(result);

        when(restTemplate.exchange(
                eq("http://192.168.1.100:8086/simulator/device/info"),
                eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class)))
                .thenReturn(response);

        SimDeviceInfo actual = driver.getInfo(endpoint);
        assertNotNull(actual);
        assertEquals("TestDevice", actual.getDeviceName());
    }

    @Test
    void getInfo_codeNot1_shouldReturnNull() {
        Result<SimDeviceInfo> result = Result.error("error");
        ResponseEntity<Result<SimDeviceInfo>> response = ResponseEntity.ok(result);

        when(restTemplate.exchange(
                eq("http://192.168.1.100:8086/simulator/device/info"),
                eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class)))
                .thenReturn(response);

        SimDeviceInfo actual = driver.getInfo(endpoint);
        assertNull(actual);
    }

    @Test
    void getInfo_nullBody_shouldReturnNull() {
        ResponseEntity<Result<SimDeviceInfo>> response = ResponseEntity.ok(null);

        when(restTemplate.exchange(
                eq("http://192.168.1.100:8086/simulator/device/info"),
                eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class)))
                .thenReturn(response);

        SimDeviceInfo actual = driver.getInfo(endpoint);
        assertNull(actual);
    }

    // ========== getCapability ==========

    @Test
    void getCapability_success_shouldReturnCapability() {
        SimDeviceCapability cap = new SimDeviceCapability();
        cap.setMaxWindows(4);
        cap.setMaxResolution("1920x1080");
        Result<SimDeviceCapability> result = Result.success(cap);
        ResponseEntity<Result<SimDeviceCapability>> response = ResponseEntity.ok(result);

        when(restTemplate.exchange(
                eq("http://192.168.1.100:8086/simulator/device/capability"),
                eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class)))
                .thenReturn(response);

        SimDeviceCapability actual = driver.getCapability(endpoint);
        assertNotNull(actual);
        assertEquals(4, actual.getMaxWindows());
        assertEquals("1920x1080", actual.getMaxResolution());
    }

    @Test
    void getCapability_codeNot1_shouldReturnNull() {
        Result<SimDeviceCapability> result = Result.error("error");
        ResponseEntity<Result<SimDeviceCapability>> response = ResponseEntity.ok(result);

        when(restTemplate.exchange(
                eq("http://192.168.1.100:8086/simulator/device/capability"),
                eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class)))
                .thenReturn(response);

        SimDeviceCapability actual = driver.getCapability(endpoint);
        assertNull(actual);
    }

    // ========== getStatus ==========

    @Test
    void getStatus_success_shouldReturnStatus() {
        SimDeviceStatus status = new SimDeviceStatus();
        status.setOnline(true);
        Result<SimDeviceStatus> result = Result.success(status);
        ResponseEntity<Result<SimDeviceStatus>> response = ResponseEntity.ok(result);

        when(restTemplate.exchange(
                eq("http://192.168.1.100:8086/simulator/device/status"),
                eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class)))
                .thenReturn(response);

        SimDeviceStatus actual = driver.getStatus(endpoint);
        assertNotNull(actual);
        assertTrue(actual.isOnline());
    }

    @Test
    void getStatus_codeNot1_shouldReturnNull() {
        Result<SimDeviceStatus> result = Result.error("error");
        ResponseEntity<Result<SimDeviceStatus>> response = ResponseEntity.ok(result);

        when(restTemplate.exchange(
                eq("http://192.168.1.100:8086/simulator/device/status"),
                eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class)))
                .thenReturn(response);

        SimDeviceStatus actual = driver.getStatus(endpoint);
        assertNull(actual);
    }

    // ========== createWindow ==========

    @Test
    void createWindow_success_shouldReturnResult() {
        SimWindow window = new SimWindow();
        window.setWindowId("win-001");
        window.setX(0);
        window.setY(0);
        window.setWidth(960);
        window.setHeight(540);

        Result<SimWindow> result = Result.success(window);
        ResponseEntity<Result<SimWindow>> response = ResponseEntity.ok(result);

        when(restTemplate.exchange(
                eq("http://192.168.1.100:8086/simulator/device/window"),
                eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(response);

        Result<SimWindow> actual = driver.createWindow(endpoint, window);
        assertNotNull(actual);
        assertEquals(1, actual.getCode());
        assertEquals("win-001", actual.getData().getWindowId());
    }

    @Test
    void createWindow_nullBody_shouldReturnErrorResult() {
        SimWindow window = new SimWindow();
        ResponseEntity<Result<SimWindow>> response = ResponseEntity.ok(null);

        when(restTemplate.exchange(
                eq("http://192.168.1.100:8086/simulator/device/window"),
                eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(response);

        Result<SimWindow> actual = driver.createWindow(endpoint, window);
        assertNotNull(actual);
        assertEquals(0, actual.getCode());
    }

    // ========== updateWindow ==========

    @Test
    void updateWindow_success_shouldReturnResult() {
        SimWindow update = new SimWindow();
        update.setX(100);
        update.setY(200);

        Result<SimWindow> result = Result.success(update);
        ResponseEntity<Result<SimWindow>> response = ResponseEntity.ok(result);

        when(restTemplate.exchange(
                eq("http://192.168.1.100:8086/simulator/device/window/win-001"),
                eq(HttpMethod.PUT), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(response);

        Result<SimWindow> actual = driver.updateWindow(endpoint, "win-001", update);
        assertNotNull(actual);
        assertEquals(1, actual.getCode());
        assertEquals(100, actual.getData().getX());
    }

    @Test
    void updateWindow_nullBody_shouldReturnErrorResult() {
        SimWindow update = new SimWindow();
        ResponseEntity<Result<SimWindow>> response = ResponseEntity.ok(null);

        when(restTemplate.exchange(
                eq("http://192.168.1.100:8086/simulator/device/window/win-001"),
                eq(HttpMethod.PUT), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(response);

        Result<SimWindow> actual = driver.updateWindow(endpoint, "win-001", update);
        assertEquals(0, actual.getCode());
    }

    // ========== closeWindow ==========

    @Test
    void closeWindow_success_shouldReturnResult() {
        Result<Void> result = Result.success();
        ResponseEntity<Result<Void>> response = ResponseEntity.ok(result);

        when(restTemplate.exchange(
                eq("http://192.168.1.100:8086/simulator/device/window/win-001"),
                eq(HttpMethod.DELETE), isNull(), any(ParameterizedTypeReference.class)))
                .thenReturn(response);

        Result<Void> actual = driver.closeWindow(endpoint, "win-001");
        assertNotNull(actual);
        assertEquals(1, actual.getCode());
    }

    @Test
    void closeWindow_nullBody_shouldReturnErrorResult() {
        ResponseEntity<Result<Void>> response = ResponseEntity.ok(null);

        when(restTemplate.exchange(
                eq("http://192.168.1.100:8086/simulator/device/window/win-001"),
                eq(HttpMethod.DELETE), isNull(), any(ParameterizedTypeReference.class)))
                .thenReturn(response);

        Result<Void> actual = driver.closeWindow(endpoint, "win-001");
        assertEquals(0, actual.getCode());
    }

    // ========== getWindows ==========

    @Test
    void getWindows_success_shouldReturnList() {
        SimWindow win = new SimWindow();
        win.setWindowId("win-001");
        Result<List<SimWindow>> result = Result.success(List.of(win));
        ResponseEntity<Result<List<SimWindow>>> response = ResponseEntity.ok(result);

        when(restTemplate.exchange(
                eq("http://192.168.1.100:8086/simulator/device/windows"),
                eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class)))
                .thenReturn(response);

        List<SimWindow> actual = driver.getWindows(endpoint);
        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals("win-001", actual.get(0).getWindowId());
    }

    @Test
    void getWindows_codeNot1_shouldReturnNull() {
        Result<List<SimWindow>> result = Result.error("error");
        ResponseEntity<Result<List<SimWindow>>> response = ResponseEntity.ok(result);

        when(restTemplate.exchange(
                eq("http://192.168.1.100:8086/simulator/device/windows"),
                eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class)))
                .thenReturn(response);

        List<SimWindow> actual = driver.getWindows(endpoint);
        assertNull(actual);
    }

    // ========== notifyWindow ==========

    @Test
    void notifyWindow_success_shouldReturnResult() {
        SimWindow win = new SimWindow();
        win.setWindowId("win-001");
        Result<Void> result = Result.success();
        ResponseEntity<Result<Void>> response = ResponseEntity.ok(result);

        when(restTemplate.exchange(
                eq("http://192.168.1.100:8086/simulator/device/window/notify"),
                eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(response);

        Result<Void> actual = driver.notifyWindow(endpoint, List.of(win));
        assertNotNull(actual);
        assertEquals(1, actual.getCode());
    }

    @Test
    void notifyWindow_nullBody_shouldReturnErrorResult() {
        ResponseEntity<Result<Void>> response = ResponseEntity.ok(null);

        when(restTemplate.exchange(
                eq("http://192.168.1.100:8086/simulator/device/window/notify"),
                eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(response);

        Result<Void> actual = driver.notifyWindow(endpoint, List.of());
        assertEquals(0, actual.getCode());
    }
}