package com.example.demo.simulator.tlv.output.core;

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
 * SimDeviceManager（TLV 输出模拟器）单元测试。
 * <p>
 * 覆盖窗口 CRUD、通道校验、maxWindows 上限、以及更新窗口的「仅改传入字段」语义。
 */
@ExtendWith(MockitoExtension.class)
class SimDeviceManagerTest {

    @Mock
    private DeviceConfig config;

    private SimDeviceManager manager;

    @BeforeEach
    void setUp() {
        SimDeviceInfo info = new SimDeviceInfo();
        info.setDeviceName("TLV-Output-01");
        info.setDeviceType("TLV");
        info.setDeviceCategory("OUTPUT");
        info.setOutputChannel1("OUT-1");
        info.setOutputChannel2("OUT-2");

        SimDeviceCapability cap = new SimDeviceCapability();
        cap.setMaxWindows(4);
        cap.setSupportMove(true);
        cap.setSupportResize(true);
        cap.setSupportOverlay(true);
        cap.setChannelCount(2);

        lenient().when(config.getDeviceInfo()).thenReturn(info);
        lenient().when(config.getDeviceCapability()).thenReturn(cap);
        manager = new SimDeviceManager(config);
    }

    // ========== 设备信息 / 能力 / 状态 ==========

    @Test
    void getDeviceInfo_shouldReturnInfo() {
        assertEquals("TLV-Output-01", manager.getDeviceInfo().getDeviceName());
    }

    @Test
    void getDeviceCapability_shouldReturnCapability() {
        assertEquals(4, manager.getDeviceCapability().getMaxWindows());
    }

    @Test
    void getOutputChannels_shouldCollectNonEmptyChannels() {
        assertEquals(List.of("OUT-1", "OUT-2"), manager.getOutputChannels());
    }

    @Test
    void getDeviceStatus_shouldReturnOnlineAndWindowCount() {
        manager.createWindow("win-1", "OUT-1", 0, 0, 100, 100);
        SimDeviceStatus status = manager.getDeviceStatus();
        assertTrue(status.isOnline());
        assertEquals(1, status.getWindowCount());
        assertNotNull(status.getUptime());
    }

    // ========== 输出通道校验 ==========

    @Test
    void isValidOutputChannel_valid_shouldReturnTrue() {
        assertTrue(manager.isValidOutputChannel("OUT-1"));
        assertTrue(manager.isValidOutputChannel("OUT-2"));
    }

    @Test
    void isValidOutputChannel_invalid_shouldReturnFalse() {
        assertFalse(manager.isValidOutputChannel("OUT-3"));
        assertFalse(manager.isValidOutputChannel("HDMI-1"));
        assertFalse(manager.isValidOutputChannel(null));
        assertFalse(manager.isValidOutputChannel(""));
    }

    // ========== 创建窗口 ==========

    @Test
    void createWindow_shouldCreate() {
        SimWindow w = manager.createWindow("win-1", "OUT-1", 10, 20, 800, 600);
        assertEquals("win-1", w.getWindowId());
        assertEquals("OUT-1", w.getChannelName());
        assertEquals(10, w.getX());
        assertEquals(20, w.getY());
        assertEquals(800, w.getWidth());
        assertEquals(600, w.getHeight());
        assertEquals("Stream", w.getSourceType());
        assertNotNull(w.getCreateTime());
        assertEquals(1, manager.getWindows().size());
    }

    @Test
    void createWindow_nullCoordinates_shouldUseDefaults() {
        SimWindow w = manager.createWindow("win-1", "OUT-1", null, null, null, null);
        assertEquals(0, w.getX());
        assertEquals(0, w.getY());
        assertEquals(1920, w.getWidth());
        assertEquals(1080, w.getHeight());
    }

    @Test
    void createWindow_duplicateId_shouldThrow() {
        manager.createWindow("win-1", "OUT-1", 0, 0, 100, 100);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> manager.createWindow("win-1", "OUT-2", 0, 0, 100, 100));
        assertTrue(ex.getMessage().contains("窗口已存在"));
    }

    @Test
    void createWindow_invalidChannel_shouldThrow() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> manager.createWindow("win-1", "OUT-99", 0, 0, 100, 100));
        assertTrue(ex.getMessage().contains("通道名无效"));
    }

    @Test
    void createWindow_exceedMaxWindows_shouldThrow() {
        manager.createWindow("win-1", "OUT-1", 0, 0, 100, 100);
        manager.createWindow("win-2", "OUT-1", 0, 0, 100, 100);
        manager.createWindow("win-3", "OUT-1", 0, 0, 100, 100);
        manager.createWindow("win-4", "OUT-1", 0, 0, 100, 100);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> manager.createWindow("win-5", "OUT-1", 0, 0, 100, 100));
        assertTrue(ex.getMessage().contains("上限"));
    }

    // ========== 更新窗口 ==========

    @Test
    void updateWindow_shouldUpdateOnlyProvidedFields() {
        manager.createWindow("win-1", "OUT-1", 10, 20, 800, 600);
        SimWindow w = manager.updateWindow("win-1", null, 100, 200, null, null);
        assertEquals(100, w.getX());
        assertEquals(200, w.getY());
        assertEquals(800, w.getWidth());   // 保持原值
        assertEquals(600, w.getHeight());  // 保持原值
        assertEquals("OUT-1", w.getChannelName()); // 未传通道，保持原值
    }

    @Test
    void updateWindow_shouldSwitchChannel() {
        manager.createWindow("win-1", "OUT-1", 10, 20, 800, 600);
        SimWindow w = manager.updateWindow("win-1", "OUT-2", null, null, null, null);
        assertEquals("OUT-2", w.getChannelName());
    }

    @Test
    void updateWindow_notExists_shouldThrow() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> manager.updateWindow("win-999", null, 1, 1, 1, 1));
        assertTrue(ex.getMessage().contains("窗口不存在"));
    }

    @Test
    void updateWindow_invalidChannel_shouldThrow() {
        manager.createWindow("win-1", "OUT-1", 10, 20, 800, 600);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> manager.updateWindow("win-1", "OUT-99", null, null, null, null));
        assertTrue(ex.getMessage().contains("通道名无效"));
    }

    // ========== 关闭窗口 ==========

    @Test
    void closeWindow_shouldRemove() {
        manager.createWindow("win-1", "OUT-1", 0, 0, 100, 100);
        SimWindow removed = manager.closeWindow("win-1");
        assertEquals("win-1", removed.getWindowId());
        assertEquals(0, manager.getWindows().size());
    }

    @Test
    void closeWindow_notExists_shouldThrow() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> manager.closeWindow("win-999"));
        assertTrue(ex.getMessage().contains("窗口不存在"));
    }

    // ========== 窗口查询 ==========

    @Test
    void getWindows_shouldReturnAll() {
        manager.createWindow("win-1", "OUT-1", 0, 0, 100, 100);
        manager.createWindow("win-2", "OUT-2", 0, 0, 100, 100);
        assertEquals(2, manager.getWindows().size());
    }

}