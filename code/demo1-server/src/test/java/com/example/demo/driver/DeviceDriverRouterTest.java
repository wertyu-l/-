package com.example.demo.driver;

import com.example.demo.common.Result;
import com.example.demo.model.SimDeviceCapability;
import com.example.demo.model.SimDeviceInfo;
import com.example.demo.model.SimDeviceStatus;
import com.example.demo.model.SimWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DeviceDriverRouter 单元测试
 * <p>
 * 验证按设备类型（REST/TLV）正确分发到对应驱动实现。
 */
@ExtendWith(MockitoExtension.class)
class DeviceDriverRouterTest {

    @Mock
    private RestDeviceDriver restDeviceDriver;

    @Mock
    private TlvDeviceDriver tlvDeviceDriver;

    @InjectMocks
    private DeviceDriverRouter router;

    private DeviceEndpoint restEndpoint;
    private DeviceEndpoint tlvEndpoint;

    @BeforeEach
    void setUp() {
        restEndpoint = new DeviceEndpoint();
        restEndpoint.setDeviceType("REST");
        restEndpoint.setBaseUrl("http://192.168.1.100:8086");

        tlvEndpoint = new DeviceEndpoint();
        tlvEndpoint.setDeviceType("TLV");
        tlvEndpoint.setBaseUrl("udp://192.168.1.100:8092");
    }

    // ========== getInfo ==========

    @Test
    void getInfo_restEndpoint_shouldDelegateToRestDriver() {
        SimDeviceInfo info = new SimDeviceInfo();
        info.setDeviceName("REST-Device");
        when(restDeviceDriver.getInfo(restEndpoint)).thenReturn(info);

        SimDeviceInfo result = router.getInfo(restEndpoint);
        assertEquals("REST-Device", result.getDeviceName());
        verify(restDeviceDriver).getInfo(restEndpoint);
        verifyNoInteractions(tlvDeviceDriver);
    }

    @Test
    void getInfo_tlvEndpoint_shouldDelegateToTlvDriver() {
        SimDeviceInfo info = new SimDeviceInfo();
        info.setDeviceName("TLV-Device");
        when(tlvDeviceDriver.getInfo(tlvEndpoint)).thenReturn(info);

        SimDeviceInfo result = router.getInfo(tlvEndpoint);
        assertEquals("TLV-Device", result.getDeviceName());
        verify(tlvDeviceDriver).getInfo(tlvEndpoint);
        verifyNoInteractions(restDeviceDriver);
    }

    @Test
    void getInfo_nullDeviceType_shouldDelegateToRestDriver() {
        restEndpoint.setDeviceType(null);
        SimDeviceInfo info = new SimDeviceInfo();
        when(restDeviceDriver.getInfo(restEndpoint)).thenReturn(info);

        router.getInfo(restEndpoint);
        verify(restDeviceDriver).getInfo(restEndpoint);
        verifyNoInteractions(tlvDeviceDriver);
    }

    // ========== getCapability ==========

    @Test
    void getCapability_restEndpoint_shouldDelegateToRestDriver() {
        SimDeviceCapability cap = new SimDeviceCapability();
        cap.setMaxWindows(4);
        when(restDeviceDriver.getCapability(restEndpoint)).thenReturn(cap);

        SimDeviceCapability result = router.getCapability(restEndpoint);
        assertEquals(4, result.getMaxWindows());
        verify(restDeviceDriver).getCapability(restEndpoint);
    }

    @Test
    void getCapability_tlvEndpoint_shouldDelegateToTlvDriver() {
        SimDeviceCapability cap = new SimDeviceCapability();
        cap.setMaxWindows(8);
        when(tlvDeviceDriver.getCapability(tlvEndpoint)).thenReturn(cap);

        SimDeviceCapability result = router.getCapability(tlvEndpoint);
        assertEquals(8, result.getMaxWindows());
        verify(tlvDeviceDriver).getCapability(tlvEndpoint);
    }

    // ========== getStatus ==========

    @Test
    void getStatus_restEndpoint_shouldDelegateToRestDriver() {
        SimDeviceStatus status = new SimDeviceStatus();
        status.setOnline(true);
        when(restDeviceDriver.getStatus(restEndpoint)).thenReturn(status);

        SimDeviceStatus result = router.getStatus(restEndpoint);
        assertTrue(result.isOnline());
        verify(restDeviceDriver).getStatus(restEndpoint);
    }

    @Test
    void getStatus_tlvEndpoint_shouldDelegateToTlvDriver() {
        SimDeviceStatus status = new SimDeviceStatus();
        status.setOnline(true);
        when(tlvDeviceDriver.getStatus(tlvEndpoint)).thenReturn(status);

        SimDeviceStatus result = router.getStatus(tlvEndpoint);
        assertTrue(result.isOnline());
        verify(tlvDeviceDriver).getStatus(tlvEndpoint);
    }

    // ========== createWindow ==========

    @Test
    void createWindow_restEndpoint_shouldDelegateToRestDriver() {
        SimWindow window = new SimWindow();
        window.setWindowId("win-001");
        Result<SimWindow> expected = Result.success(window);
        when(restDeviceDriver.createWindow(eq(restEndpoint), any(SimWindow.class))).thenReturn(expected);

        Result<SimWindow> result = router.createWindow(restEndpoint, window);
        assertEquals(1, result.getCode());
        assertEquals("win-001", result.getData().getWindowId());
        verify(restDeviceDriver).createWindow(eq(restEndpoint), any(SimWindow.class));
    }

    @Test
    void createWindow_tlvEndpoint_shouldDelegateToTlvDriver() {
        SimWindow window = new SimWindow();
        window.setWindowId("win-002");
        Result<SimWindow> expected = Result.success(window);
        when(tlvDeviceDriver.createWindow(eq(tlvEndpoint), any(SimWindow.class))).thenReturn(expected);

        Result<SimWindow> result = router.createWindow(tlvEndpoint, window);
        assertEquals(1, result.getCode());
        assertEquals("win-002", result.getData().getWindowId());
        verify(tlvDeviceDriver).createWindow(eq(tlvEndpoint), any(SimWindow.class));
    }

    // ========== updateWindow ==========

    @Test
    void updateWindow_restEndpoint_shouldDelegateToRestDriver() {
        SimWindow update = new SimWindow();
        update.setX(100);
        Result<SimWindow> expected = Result.success(update);
        when(restDeviceDriver.updateWindow(eq(restEndpoint), eq("win-001"), any(SimWindow.class))).thenReturn(expected);

        Result<SimWindow> result = router.updateWindow(restEndpoint, "win-001", update);
        assertEquals(1, result.getCode());
        assertEquals(100, result.getData().getX());
    }

    @Test
    void updateWindow_tlvEndpoint_shouldDelegateToTlvDriver() {
        SimWindow update = new SimWindow();
        update.setY(200);
        Result<SimWindow> expected = Result.success(update);
        when(tlvDeviceDriver.updateWindow(eq(tlvEndpoint), eq("win-001"), any(SimWindow.class))).thenReturn(expected);

        Result<SimWindow> result = router.updateWindow(tlvEndpoint, "win-001", update);
        assertEquals(1, result.getCode());
        assertEquals(200, result.getData().getY());
    }

    // ========== closeWindow ==========

    @Test
    void closeWindow_restEndpoint_shouldDelegateToRestDriver() {
        Result<Void> expected = Result.success();
        when(restDeviceDriver.closeWindow(restEndpoint, "win-001")).thenReturn(expected);

        Result<Void> result = router.closeWindow(restEndpoint, "win-001");
        assertEquals(1, result.getCode());
        verify(restDeviceDriver).closeWindow(restEndpoint, "win-001");
    }

    @Test
    void closeWindow_tlvEndpoint_shouldDelegateToTlvDriver() {
        Result<Void> expected = Result.success();
        when(tlvDeviceDriver.closeWindow(tlvEndpoint, "win-001")).thenReturn(expected);

        Result<Void> result = router.closeWindow(tlvEndpoint, "win-001");
        assertEquals(1, result.getCode());
        verify(tlvDeviceDriver).closeWindow(tlvEndpoint, "win-001");
    }

    // ========== getWindows ==========

    @Test
    void getWindows_restEndpoint_shouldDelegateToRestDriver() {
        SimWindow w = new SimWindow();
        w.setWindowId("win-001");
        when(restDeviceDriver.getWindows(restEndpoint)).thenReturn(List.of(w));

        List<SimWindow> result = router.getWindows(restEndpoint);
        assertEquals(1, result.size());
        assertEquals("win-001", result.get(0).getWindowId());
    }

    @Test
    void getWindows_tlvEndpoint_shouldDelegateToTlvDriver() {
        when(tlvDeviceDriver.getWindows(tlvEndpoint)).thenReturn(List.of());

        List<SimWindow> result = router.getWindows(tlvEndpoint);
        assertTrue(result.isEmpty());
    }

    // ========== notifyWindow ==========

    @Test
    void notifyWindow_restEndpoint_shouldDelegateToRestDriver() {
        Result<Void> expected = Result.success();
        SimWindow w = new SimWindow();
        w.setWindowId("win-001");
        when(restDeviceDriver.notifyWindow(eq(restEndpoint), anyList())).thenReturn(expected);

        Result<Void> result = router.notifyWindow(restEndpoint, List.of(w));
        assertEquals(1, result.getCode());
        verify(restDeviceDriver).notifyWindow(eq(restEndpoint), anyList());
    }

    @Test
    void notifyWindow_tlvEndpoint_shouldDelegateToTlvDriver() {
        Result<Void> expected = Result.success();
        when(tlvDeviceDriver.notifyWindow(eq(tlvEndpoint), anyList())).thenReturn(expected);

        Result<Void> result = router.notifyWindow(tlvEndpoint, List.of());
        assertEquals(1, result.getCode());
        verify(tlvDeviceDriver).notifyWindow(eq(tlvEndpoint), anyList());
    }
}