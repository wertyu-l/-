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

    /**
     * 获取设备信息应返回初始化时配置的设备名称
     */
    @Test
    void getDeviceInfo_shouldReturnInfo() {
        assertEquals("TLV-Output-01", manager.getDeviceInfo().getDeviceName());
    }

    /**
     * 获取设备能力应返回初始化时配置的 maxWindows
     */
    @Test
    void getDeviceCapability_shouldReturnCapability() {
        assertEquals(4, manager.getDeviceCapability().getMaxWindows());
    }

    /**
     * 获取输出通道列表应收集所有非空通道名
     */
    @Test
    void getOutputChannels_shouldCollectNonEmptyChannels() {
        assertEquals(List.of("OUT-1", "OUT-2"), manager.getOutputChannels());
    }

    /**
     * 获取设备状态应返回在线状态和当前窗口数
     */
    @Test
    void getDeviceStatus_shouldReturnOnlineAndWindowCount() {
        manager.createWindow("win-1", "OUT-1", 0, 0, 100, 100, null, null);
        SimDeviceStatus status = manager.getDeviceStatus();
        assertTrue(status.isOnline());
        assertEquals(1, status.getWindowCount());
        assertNotNull(status.getUptime());
    }

    // ========== 输出通道校验 ==========

    /**
     * 有效通道名应返回 true
     */
    @Test
    void isValidOutputChannel_valid_shouldReturnTrue() {
        assertTrue(manager.isValidOutputChannel("OUT-1"));
        assertTrue(manager.isValidOutputChannel("OUT-2"));
    }

    /**
     * 无效通道名（不存在、null、空字符串）应返回 false
     */
    @Test
    void isValidOutputChannel_invalid_shouldReturnFalse() {
        assertFalse(manager.isValidOutputChannel("OUT-3"));
        assertFalse(manager.isValidOutputChannel("HDMI-1"));
        assertFalse(manager.isValidOutputChannel(null));
        assertFalse(manager.isValidOutputChannel(""));
    }

    // ========== 创建窗口 ==========

    /**
     * 正常创建窗口应设置所有字段并加入窗口列表
     */
    @Test
    void createWindow_shouldCreate() {
        SimWindow w = manager.createWindow("win-1", "OUT-1", 10, 20, 800, 600, null, null);
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

    /**
     * 传入 null 坐标时应使用默认值（0,0,1920,1080）
     */
    @Test
    void createWindow_nullCoordinates_shouldUseDefaults() {
        SimWindow w = manager.createWindow("win-1", "OUT-1", null, null, null, null, null, null);
        assertEquals(0, w.getX());
        assertEquals(0, w.getY());
        assertEquals(1920, w.getWidth());
        assertEquals(1080, w.getHeight());
    }

    /**
     * 同一窗口 ID 和通道名重复创建应抛出异常
     */
    @Test
    void createWindow_duplicateId_shouldThrow() {
        manager.createWindow("win-1", "OUT-1", 0, 0, 100, 100, null, null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> manager.createWindow("win-1", "OUT-1", 0, 0, 100, 100, null, null));
        assertTrue(ex.getMessage().contains("窗口已存在"));
    }

    /**
     * 使用无效通道名创建窗口应抛出异常
     */
    @Test
    void createWindow_invalidChannel_shouldThrow() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> manager.createWindow("win-1", "OUT-99", 0, 0, 100, 100, null, null));
        assertTrue(ex.getMessage().contains("通道名无效"));
    }

    /**
     * 通道窗口数达到 maxWindows 上限后继续创建应抛出异常
     */
    @Test
    void createWindow_exceedMaxWindows_shouldThrow() {
        manager.createWindow("win-1", "OUT-1", 0, 0, 100, 100, null, null);
        manager.createWindow("win-2", "OUT-1", 0, 0, 100, 100, null, null);
        manager.createWindow("win-3", "OUT-1", 0, 0, 100, 100, null, null);
        manager.createWindow("win-4", "OUT-1", 0, 0, 100, 100, null, null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> manager.createWindow("win-5", "OUT-1", 0, 0, 100, 100, null, null));
        assertTrue(ex.getMessage().contains("上限"));
    }

    // ========== 更新窗口 ==========

    /**
     * 更新窗口时应仅修改传入的非 null 字段，未传入字段保持原值
     */
    @Test
    void updateWindow_shouldUpdateOnlyProvidedFields() {
        manager.createWindow("win-1", "OUT-1", 10, 20, 800, 600, null, null);
        SimWindow w = manager.updateWindow("win-1", null, 100, 200, null, null);
        assertEquals(100, w.getX());
        assertEquals(200, w.getY());
        assertEquals(800, w.getWidth());   // 保持原值
        assertEquals(600, w.getHeight());  // 保持原值
        assertEquals("OUT-1", w.getChannelName()); // 未传通道，保持原值
    }

    /**
     * 通过 null channelName 可触发前缀匹配找到窗口
     */
    @Test
    void updateWindow_shouldFindByPrefix() {
        manager.createWindow("win-1", "OUT-1", 10, 20, 800, 600, null, null);
        SimWindow w = manager.updateWindow("win-1", null, null, null, null, null);
        assertEquals("OUT-1", w.getChannelName());
    }

    /**
     * 更新不存在的窗口应抛出异常
     */
    @Test
    void updateWindow_notExists_shouldThrow() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> manager.updateWindow("win-999", null, 1, 1, 1, 1));
        assertTrue(ex.getMessage().contains("窗口不存在"));
    }

    /**
     * 使用非空但无效的通道名更新时，因 key 不匹配先触发"窗口不存在"而非"通道名无效"
     */
    @Test
    void updateWindow_invalidChannel_shouldThrow() {
        manager.createWindow("win-1", "OUT-1", 10, 20, 800, 600, null, null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> manager.updateWindow("win-1", "OUT-99", null, null, null, null));
        assertTrue(ex.getMessage().contains("窗口不存在"));
    }

    // ========== 关闭窗口 ==========

    /**
     * 关闭存在的窗口应将其从列表中移除并返回
     */
    @Test
    void closeWindow_shouldRemove() {
        manager.createWindow("win-1", "OUT-1", 0, 0, 100, 100, null, null);
        SimWindow removed = manager.closeWindow("win-1");
        assertEquals("win-1", removed.getWindowId());
        assertEquals(0, manager.getWindows().size());
    }

    /**
     * 关闭不存在的窗口应抛出异常
     */
    @Test
    void closeWindow_notExists_shouldThrow() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> manager.closeWindow("win-999"));
        assertTrue(ex.getMessage().contains("窗口不存在"));
    }

    // ========== 窗口查询 ==========

    /**
     * 获取所有窗口应返回全部已创建的窗口
     */
    @Test
    void getWindows_shouldReturnAll() {
        manager.createWindow("win-1", "OUT-1", 0, 0, 100, 100, null, null);
        manager.createWindow("win-2", "OUT-2", 0, 0, 100, 100, null, null);
        assertEquals(2, manager.getWindows().size());
    }

    /**
     * 按 windowId 前缀匹配查找窗口应返回第一个匹配项
     */
    @Test
    void findWindow_shouldReturnWindowByPrefix() {
        manager.createWindow("win-1", "OUT-1", 10, 20, 800, 600, null, null);
        SimWindow w = manager.findWindow("win-1");
        assertNotNull(w);
        assertEquals("win-1", w.getWindowId());
        assertEquals("OUT-1", w.getChannelName());
    }

    /**
     * 查找不存在的窗口应返回 null
     */
    @Test
    void findWindow_notExists_shouldReturnNull() {
        assertNull(manager.findWindow("win-999"));
    }

    // ========== sourceType / sourceUrl 传递 ==========

    /**
     * 创建窗口时传入 sourceType 和 sourceUrl 应使用传入值
     */
    @Test
    void createWindow_withSourceTypeAndUrl_shouldUseProvidedValues() {
        SimWindow w = manager.createWindow("win-1", "OUT-1", 0, 0, 100, 100, "HDMI", "rtsp://cam1/stream");
        assertEquals("HDMI", w.getSourceType());
        assertEquals("rtsp://cam1/stream", w.getSourceUrl());
    }

    /**
     * sourceType 为 null 时应根据通道名自动推断为 "Stream"
     */
    @Test
    void createWindow_nullSourceType_shouldInferFromChannel() {
        SimWindow w = manager.createWindow("win-1", "OUT-1", 0, 0, 100, 100, null, "rtsp://cam1/stream");
        assertEquals("Stream", w.getSourceType());
        assertEquals("rtsp://cam1/stream", w.getSourceUrl());
    }

    // ========== closeWindow 前缀匹配（多窗口同 windowId） ==========

    /**
     * 同一 windowId 在多个通道下创建窗口时，closeWindow 应通过前缀匹配删除所有匹配项
     */
    @Test
    void closeWindow_shouldRemoveAllMatchingWindowId() {
        manager.createWindow("win-1", "OUT-1", 0, 0, 100, 100, null, null);
        manager.createWindow("win-2", "OUT-1", 0, 0, 100, 100, null, null);
        assertEquals(2, manager.getWindows().size());
        manager.closeWindow("win-1");
        assertEquals(1, manager.getWindows().size());
        assertEquals("win-2", manager.getWindows().get(0).getWindowId());
    }

}