package com.example.demo.simulator.output.core;

import com.example.demo.model.SimDeviceCapability;
import com.example.demo.model.SimDeviceInfo;
import com.example.demo.model.SimDeviceStatus;
import com.example.demo.model.SimWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SimDeviceManager（输出模拟器）单元测试
 * <p>
 * 覆盖输出模拟设备的窗口创建、删除、状态查询、设备信息/能力查询的完整流程，
 * 重点验证输出通道校验（OUT-1~OUT-5）、能力声明（移动/缩放/叠加）、能力更新联动设备信息。
 */
@ExtendWith(MockitoExtension.class)
class SimDeviceManagerTest {

    @Mock
    private DeviceRepository repo;

    private SimDeviceManager manager;

    @BeforeEach
    void setUp() {
        SimDeviceInfo info = new SimDeviceInfo();
        info.setDeviceName("REST-Output-01");
        info.setDeviceType("REST");
        info.setDeviceCategory("OUTPUT");
        info.setOutputChannel1("OUT-1");
        info.setOutputChannel2("OUT-2");
        info.setOutputChannel3("OUT-3");
        info.setOutputChannel4("OUT-4");
        info.setOutputChannel5("OUT-5");
        info.setMaxWindows(4);
        info.setMaxResolution("1920x1080");

        SimDeviceCapability cap = new SimDeviceCapability();
        cap.setSupportMove(true);
        cap.setSupportResize(true);
        cap.setSupportOverlay(true);
        cap.setMaxWindows(4);
        cap.setMaxResolution("1920x1080");
        cap.setOutputChannel1("OUT-1");
        cap.setOutputChannel2("OUT-2");
        cap.setOutputChannel3("OUT-3");
        cap.setOutputChannel4("OUT-4");
        cap.setOutputChannel5("OUT-5");
        cap.setChannelCount(5);

        when(repo.loadDeviceInfo()).thenReturn(info);
        when(repo.loadDeviceCapability()).thenReturn(cap);
        manager = new SimDeviceManager(repo);
    }

    // ========== 窗口创建 ==========

    /**
     * 正常创建窗口应返回包含 windowId 和创建时间的结果
     */
    @Test
    void createWindow_valid_shouldSucceed() {
        SimWindow window = new SimWindow();
        window.setWindowId("win-001");
        window.setChannelName("OUT-1");
        when(repo.findByWindowIdAndChannel("win-001", "OUT-1")).thenReturn(null);
        SimWindow result = manager.createWindow(window);
        assertNotNull(result);
        assertEquals("win-001", result.getWindowId());
        assertNotNull(result.getCreateTime());
        verify(repo).insertWindow(window);
    }

    /**
     * 重复创建同窗口应返回 null
     */
    @Test
    void createWindow_duplicate_shouldReturnNull() {
        SimWindow window = new SimWindow();
        window.setWindowId("win-001");
        window.setChannelName("OUT-1");
        when(repo.findByWindowIdAndChannel("win-001", "OUT-1")).thenReturn(new SimWindow());
        assertNull(manager.createWindow(window));
    }

    /**
     * 使用无效通道创建窗口应返回 null
     */
    @Test
    void createWindow_invalidChannel_shouldReturnNull() {
        SimWindow window = new SimWindow();
        window.setWindowId("win-001");
        window.setChannelName("INVALID");
        when(repo.findByWindowIdAndChannel("win-001", "INVALID")).thenReturn(null);
        assertNull(manager.createWindow(window));
    }

    /**
     * 未指定尺寸/位置时应使用默认值（1920x1080, 原点）
     */
    @Test
    void createWindow_nullDefaults_shouldSetDefaults() {
        SimWindow window = new SimWindow();
        window.setWindowId("win-001");
        window.setChannelName("OUT-1");
        when(repo.findByWindowIdAndChannel("win-001", "OUT-1")).thenReturn(null);
        SimWindow result = manager.createWindow(window);
        assertEquals(0, result.getX());
        assertEquals(0, result.getY());
        assertEquals(1920, result.getWidth());
        assertEquals(1080, result.getHeight());
    }


    /**
     * 输出设备的 sourceType 默认为空字符串（非 HDMI 不推断）
     */
    @Test
    void createWindow_emptySourceType_shouldSetEmpty() {
        SimWindow window = new SimWindow();
        window.setWindowId("win-001");
        window.setChannelName("OUT-1");
        when(repo.findByWindowIdAndChannel("win-001", "OUT-1")).thenReturn(null);
        SimWindow result = manager.createWindow(window);
        assertEquals("", result.getSourceType());
    }

    // ========== 窗口关闭/删除 ==========

    /**
     * 关闭存在的窗口应返回 true
     */
    @Test
    void closeWindow_success_shouldReturnTrue() {
        when(repo.deleteWindow("win-001")).thenReturn(true);
        assertTrue(manager.closeWindow("win-001"));
    }

    /**
     * 关闭不存在的窗口应返回 false
     */
    @Test
    void closeWindow_notFound_shouldReturnFalse() {
        when(repo.deleteWindow("win-999")).thenReturn(false);
        assertFalse(manager.closeWindow("win-999"));
    }

    // ========== 设备状态查询 ==========

    /**
     * 正常查询设备状态，应返回在线状态和窗口数量
     */
    @Test
    void getDeviceStatus_shouldReturnStatus() {
        when(repo.countWindows()).thenReturn(2);
        SimDeviceStatus status = manager.getDeviceStatus();
        assertTrue(status.isOnline());
        assertEquals(2, status.getWindowCount());
        assertNotNull(status.getUptime());
    }

    // ========== 输出通道校验 ==========

    /**
     * 有效的输出通道（OUT-1~OUT-5）应返回 true
     */
    @Test
    void isValidOutputChannel_valid_shouldReturnTrue() {
        assertTrue(manager.isValidOutputChannel("OUT-1"));
        assertTrue(manager.isValidOutputChannel("OUT-5"));
    }

    /**
     * 无效的输出通道应返回 false
     */
    @Test
    void isValidOutputChannel_invalid_shouldReturnFalse() {
        assertFalse(manager.isValidOutputChannel("OUT-6"));
        assertFalse(manager.isValidOutputChannel("HDMI-1"));
    }

    /**
     * null 通道名应返回 false（防 NPE）
     */
    @Test
    void isValidOutputChannel_null_shouldReturnFalse() {
        assertFalse(manager.isValidOutputChannel(null));
    }

    /**
     * 空字符串通道名应返回 false
     */
    @Test
    void isValidOutputChannel_empty_shouldReturnFalse() {
        assertFalse(manager.isValidOutputChannel(""));
    }

    // ========== 设备信息查询 ==========

    /**
     * 正常查询设备信息，验证名称、类别和最大窗口数
     */
    @Test
    void getDeviceInfo_shouldReturnInfo() {
        SimDeviceInfo info = manager.getDeviceInfo();
        assertEquals("REST-Output-01", info.getDeviceName());
        assertEquals("OUTPUT", info.getDeviceCategory());
        assertEquals(4, info.getMaxWindows());
    }

    /**
     * 正常查询设备能力，验证能力字段和最大窗口数
     */
    @Test
    void getDeviceCapability_shouldReturnCapability() {
        SimDeviceCapability cap = manager.getDeviceCapability();
        assertTrue(cap.isSupportMove());
        assertTrue(cap.isSupportResize());
        assertEquals(4, cap.getMaxWindows());
    }

    // ========== 设备能力更新 ==========

    /**
     * 更新设备能力应修改所有字段并联动更新设备信息
     */
    @Test
    void updateDeviceCapability_shouldUpdateAllFields() {
        SimDeviceCapability newCap = new SimDeviceCapability();
        newCap.setSupportMove(false);
        newCap.setSupportResize(false);
        newCap.setSupportOverlay(false);
        newCap.setMaxWindows(2);
        newCap.setMaxResolution("3840x2160");
        newCap.setChannelCount(1);
        newCap.setOutputChannel1("OUT-A");
        newCap.setOutputChannel2(null);
        newCap.setOutputChannel3(null);
        newCap.setOutputChannel4(null);
        newCap.setOutputChannel5(null);
        SimDeviceCapability result = manager.updateDeviceCapability(newCap);
        assertFalse(result.isSupportMove());
        assertFalse(result.isSupportResize());
        assertFalse(result.isSupportOverlay());
        assertEquals(2, result.getMaxWindows());
        verify(repo).updateCapability(result);
        verify(repo).updateDeviceInfoFromCapability(newCap);
    }

    // ========== 窗口列表查询 ==========

    /**
     * 查询所有窗口应返回窗口列表
     */
    @Test
    void getWindows_shouldReturnList() {
        when(repo.findAllWindows()).thenReturn(List.of(new SimWindow()));
        assertEquals(1, manager.getWindows().size());
    }

    /**
     * 按 ID 查询窗口应返回对应窗口
     */
    @Test
    void getWindow_shouldReturnWindow() {
        SimWindow window = new SimWindow();
        window.setWindowId("win-001");
        when(repo.findWindowById("win-001")).thenReturn(window);
        assertEquals(window, manager.getWindow("win-001"));
    }

    // ========== 窗口数上限 ==========

    /**
     * 窗口数已达上限时创建应返回 null
     */
    @Test
    void createWindow_maxWindowsReached_shouldReturnNull() {
        SimWindow window = new SimWindow();
        window.setWindowId("win-005");
        window.setChannelName("OUT-1");
        when(repo.findByWindowIdAndChannel("win-005", "OUT-1")).thenReturn(null);
        when(repo.countWindowsByChannel("OUT-1")).thenReturn(4); // maxWindows = 4
        assertNull(manager.createWindow(window));
    }

    // ========== 窗口更新 ==========

    /**
     * 更新不存在的窗口应返回 null
     */
    @Test
    void updateWindow_notFound_shouldReturnNull() {
        when(repo.findWindowById("win-999")).thenReturn(null);
        SimWindow update = new SimWindow();
        update.setX(100);
        assertNull(manager.updateWindow("win-999", update));
    }

    /**
     * 正常更新窗口应成功修改坐标
     */
    @Test
    void updateWindow_success_shouldUpdate() {
        SimWindow existing = new SimWindow();
        existing.setWindowId("win-001");
        existing.setChannelName("OUT-1");
        existing.setX(0);
        existing.setY(0);
        existing.setWidth(960);
        existing.setHeight(540);
        when(repo.findWindowById("win-001")).thenReturn(existing);
        SimWindow update = new SimWindow();
        update.setX(100);
        update.setY(200);
        SimWindow result = manager.updateWindow("win-001", update);
        assertNotNull(result);
        assertEquals(100, result.getX());
        assertEquals(200, result.getY());
        verify(repo).updateWindow(existing);
    }

    /**
     * 设备不支持移动时，更新坐标应返回 null
     */
    @Test
    void updateWindow_moveNotSupported_shouldReturnNull() {
        SimWindow existing = new SimWindow();
        existing.setWindowId("win-001");
        existing.setChannelName("OUT-1");
        existing.setX(0);
        existing.setY(0);
        when(repo.findWindowById("win-001")).thenReturn(existing);
        manager.getDeviceCapability().setSupportMove(false);
        SimWindow update = new SimWindow();
        update.setX(100);
        assertNull(manager.updateWindow("win-001", update));
        verify(repo, never()).updateWindow(any());
    }

    /**
     * 设备不支持缩放时，更新尺寸应返回 null
     */
    @Test
    void updateWindow_resizeNotSupported_shouldReturnNull() {
        SimWindow existing = new SimWindow();
        existing.setWindowId("win-001");
        existing.setChannelName("OUT-1");
        existing.setWidth(960);
        existing.setHeight(540);
        when(repo.findWindowById("win-001")).thenReturn(existing);
        manager.getDeviceCapability().setSupportResize(false);
        SimWindow update = new SimWindow();
        update.setWidth(1920);
        assertNull(manager.updateWindow("win-001", update));
        verify(repo, never()).updateWindow(any());
    }
}