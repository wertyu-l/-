package com.example.demo.simulator.tlv.input.core;

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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SimDeviceManager（TLV 输入模拟器）单元测试。
 * <p>
 * 输入设备作为被动信号源，不提供窗口增删改查，仅维护：
 * <ul>
 *   <li>「通道-窗口占用映射」{@code channelWindows}（由 CMD_NOTIFY_WINDOW 推送维护）；</li>
 *   <li>各通道播放地址（CMD_SET_CHANNEL_URL 设置）。</li>
 * </ul>
 * 本测试覆盖 notifyWindow 的 upsert/默认值补全/信号源推断、输入通道校验、通道地址管理。
 */
@ExtendWith(MockitoExtension.class)
class SimDeviceManagerTest {

    @Mock
    private DeviceConfig config;

    private SimDeviceManager manager;

    @BeforeEach
    void setUp() {
        SimDeviceInfo info = new SimDeviceInfo();
        info.setDeviceName("TLV-Input-01");
        info.setDeviceType("TLV");
        info.setDeviceCategory("INPUT");
        info.setInputChannel1("HDMI-1");
        info.setInputChannel2("HDMI-2");

        SimDeviceCapability cap = new SimDeviceCapability();
        cap.setChannelCount(2);
        cap.setMaxWindows(0);

        lenient().when(config.getDeviceInfo()).thenReturn(info);
        lenient().when(config.getDeviceCapability()).thenReturn(cap);
        manager = new SimDeviceManager(config);
    }

    // ========== 设备信息 / 能力 / 状态 ==========

    @Test
    void getDeviceInfo_shouldReturnInfo() {
        SimDeviceInfo info = manager.getDeviceInfo();
        assertEquals("TLV-Input-01", info.getDeviceName());
        assertEquals("INPUT", info.getDeviceCategory());
    }

    @Test
    void getDeviceCapability_shouldReturnCapability() {
        assertNotNull(manager.getDeviceCapability());
    }

    @Test
    void getDeviceStatus_shouldReturnOnlineAndWindowCount() {
        manager.notifyWindow("win-1", "HDMI-1", 0, 0, 800, 600);
        SimDeviceStatus status = manager.getDeviceStatus();
        assertTrue(status.isOnline());
        assertEquals(1, status.getWindowCount());
        assertNotNull(status.getUptime());
    }

    @Test
    void getInputChannels_shouldCollectNonEmptyChannels() {
        List<String> channels = manager.getInputChannels();
        assertEquals(List.of("HDMI-1", "HDMI-2"), channels);
    }

    // ========== 输入通道校验 ==========

    @Test
    void isValidInputChannel_valid_shouldReturnTrue() {
        assertTrue(manager.isValidInputChannel("HDMI-1"));
        assertTrue(manager.isValidInputChannel("HDMI-2"));
    }

    @Test
    void isValidInputChannel_invalid_shouldReturnFalse() {
        assertFalse(manager.isValidInputChannel("HDMI-3"));
        assertFalse(manager.isValidInputChannel("OUT-1"));
    }

    @Test
    void isValidInputChannel_nullOrEmpty_shouldReturnFalse() {
        assertFalse(manager.isValidInputChannel(null));
        assertFalse(manager.isValidInputChannel(""));
    }

    // ========== 窗口信息反馈（notifyWindow） ==========

    @Test
    void notifyWindow_shouldCreateWindowAndOccupancy() {
        manager.notifyWindow("win-1", "HDMI-1", 10, 20, 800, 600);

        assertEquals(Set.of("win-1"), manager.getChannelWindows().get("HDMI-1"));
        assertEquals(1, manager.getWindows().size());

        SimWindow w = manager.getWindows().get(0);
        assertEquals("win-1", w.getWindowId());
        assertEquals("HDMI-1", w.getChannelName());
        assertEquals(10, w.getX());
        assertEquals(20, w.getY());
        assertEquals(800, w.getWidth());
        assertEquals(600, w.getHeight());
        assertEquals("HDMI", w.getSourceType());
        assertNotNull(w.getCreateTime());
    }

    @Test
    void notifyWindow_nullCoordinates_shouldUseDefaults() {
        manager.notifyWindow("win-2", "HDMI-2", null, null, null, null);

        SimWindow w = manager.getWindows().get(0);
        assertEquals(0, w.getX());
        assertEquals(0, w.getY());
        assertEquals(1920, w.getWidth());
        assertEquals(1080, w.getHeight());
    }

    @Test
    void notifyWindow_existingWindow_shouldUpsert() {
        manager.notifyWindow("win-1", "HDMI-1", 10, 20, 800, 600);
        manager.notifyWindow("win-1", "HDMI-1", 100, 200, null, null);

        assertEquals(1, manager.getWindows().size());
        SimWindow w = manager.getWindows().get(0);
        assertEquals(100, w.getX());
        assertEquals(200, w.getY());
        assertEquals(800, w.getWidth());   // 保持原值
        assertEquals(600, w.getHeight());  // 保持原值
    }

    @Test
    void notifyWindow_shouldFillSourceUrlFromChannelUrls() {
        manager.setChannelUrl("HDMI-1", "rtsp://cam1/stream");
        manager.notifyWindow("win-1", "HDMI-1", 0, 0, 0, 0);

        assertEquals("rtsp://cam1/stream", manager.getWindows().get(0).getSourceUrl());
    }

    @Test
    void notifyWindow_invalidChannel_shouldStillRecord() {
        // 输入设备侧不做业务校验（校验在 handler 层），仅记录映射
        manager.notifyWindow("win-1", "UNKNOWN", 0, 0, 100, 100);
        assertEquals(1, manager.getWindows().size());
    }

    // ========== 通道播放地址管理 ==========

    @Test
    void setChannelUrl_shouldStore() {
        manager.setChannelUrl("HDMI-1", "rtsp://cam1/stream");
        assertEquals("rtsp://cam1/stream", manager.getChannelUrls().get("HDMI-1"));
    }

    @Test
    void setChannelUrl_null_shouldStoreEmpty() {
        manager.setChannelUrl("HDMI-1", null);
        assertEquals("", manager.getChannelUrls().get("HDMI-1"));
    }

    @Test
    void getChannelUrls_shouldReturnAll() {
        manager.setChannelUrl("HDMI-1", "url1");
        manager.setChannelUrl("HDMI-2", "url2");
        assertEquals(2, manager.getChannelUrls().size());
    }

    // ========== 窗口列表查询 ==========

    @Test
    void getWindows_shouldReturnAllWindows() {
        manager.notifyWindow("win-1", "HDMI-1", 0, 0, 100, 100);
        manager.notifyWindow("win-2", "HDMI-2", 0, 0, 100, 100);
        assertEquals(2, manager.getWindows().size());
    }

}
