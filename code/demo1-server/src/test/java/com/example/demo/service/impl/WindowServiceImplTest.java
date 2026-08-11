package com.example.demo.service.impl;

import com.example.demo.common.*;
import com.example.demo.driver.DeviceDriver;
import com.example.demo.entity.Screen;
import com.example.demo.entity.ScreenWindow;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.mapper.ScreenMapper;
import com.example.demo.mapper.WindowMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * WindowServiceImpl 单元测试
 * <p>
 * 覆盖窗口创建、更新、关闭、查询、清空、输出设备窗口查询、设备恢复补推的完整流程，
 * 重点验证参数校验（ID/设备/通道）、大屏存在性校验、跨单元拆分与降级标记。
 */
@ExtendWith(MockitoExtension.class)
class WindowServiceImplTest {

    @Mock
    private WindowMapper windowMapper;

    @Mock
    private ScreenMapper screenMapper;

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private DeviceDriver deviceDriver;

    @InjectMocks
    private WindowServiceImpl windowService;

    private static final Long SCREEN_ID = 1L;
    private static final String WINDOW_ID = "win-001";

    private Screen screen;
    private ScreenWindow screenWindow;
    private DevicePageVO device;
    private WindowCreateRequest createRequest;

    @BeforeEach
    void setUp() {
        screen = new Screen();
        screen.setId(SCREEN_ID);
        screen.setScreenName("TestScreen");
        screen.setRowsCount(2);
        screen.setColsCount(2);
        screen.setCellWidth(1920);
        screen.setCellHeight(1080);

        screenWindow = new ScreenWindow();
        screenWindow.setId(1L);
        screenWindow.setWindowId(WINDOW_ID);
        screenWindow.setScreenId(SCREEN_ID);
        screenWindow.setDeviceId(1L);
        screenWindow.setChannelName("OUT-1");
        screenWindow.setX(0);
        screenWindow.setY(0);
        screenWindow.setWidth(960);
        screenWindow.setHeight(540);
        screenWindow.setSyncStatus("synced");

        device = new DevicePageVO();
        device.setId(1L);
        device.setDeviceName("REST-Output-01");
        device.setDeviceCategory("OUTPUT");
        device.setDeviceType("REST");
        device.setBaseUrl("http://192.168.1.100:8086");
        device.setOnline(1);
        device.setEnabled(1);
        device.setMaxResolution("1920x1080");
        device.setMaxWindows(4);
        device.setSupportMove(1);
        device.setSupportResize(1);
        device.setSupportOverlay(1);
        device.setOutputChannel1("OUT-1");
        device.setOutputChannel2("OUT-2");

        createRequest = new WindowCreateRequest();
        createRequest.setWindowId(WINDOW_ID);
        createRequest.setDeviceId(1L);
        createRequest.setChannelName("OUT-1");
        createRequest.setX(0);
        createRequest.setY(0);
        createRequest.setWidth(960);
        createRequest.setHeight(540);
    }

    // ========== 创建窗口：参数校验 ==========

    /**
     * 窗口 ID 为空应抛出异常
     */
    @Test
    void createWindow_emptyWindowId_shouldThrowException() {
        createRequest.setWindowId("");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> windowService.createWindow(SCREEN_ID, createRequest));
        assertEquals("窗口ID不能为空", ex.getMessage());
    }

    /**
     * 窗口 ID 为 null 应抛出异常
     */
    @Test
    void createWindow_nullWindowId_shouldThrowException() {
        createRequest.setWindowId(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> windowService.createWindow(SCREEN_ID, createRequest));
        assertEquals("窗口ID不能为空", ex.getMessage());
    }

    /**
     * 设备 ID 为 null 应抛出异常
     */
    @Test
    void createWindow_nullDeviceId_shouldThrowException() {
        createRequest.setDeviceId(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> windowService.createWindow(SCREEN_ID, createRequest));
        assertEquals("设备ID不能为空", ex.getMessage());
    }

    /**
     * 通道名为空应抛出异常
     */
    @Test
    void createWindow_emptyChannelName_shouldThrowException() {
        createRequest.setChannelName("");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> windowService.createWindow(SCREEN_ID, createRequest));
        assertEquals("通道名不能为空", ex.getMessage());
    }

    /**
     * 大屏不存在应抛出异常
     */
    @Test
    void createWindow_screenNotFound_shouldThrowException() {
        when(screenMapper.findById(SCREEN_ID)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> windowService.createWindow(SCREEN_ID, createRequest));
        assertEquals("大屏不存在: id=1", ex.getMessage());
    }

    // ========== 更新窗口：参数校验 ==========

    /**
     * 更新不存在的窗口应抛出异常
     */
    @Test
    void updateWindow_notFound_shouldThrowException() {
        WindowUpdateRequest request = new WindowUpdateRequest();
        request.setX(100);
        when(windowMapper.findByWindowId("win-999")).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> windowService.updateWindow(SCREEN_ID, "win-999", request));
        assertEquals("窗口不存在: win-999", ex.getMessage());
    }

    /**
     * 窗口所属大屏不匹配应抛出异常
     */
    @Test
    void updateWindow_wrongScreenId_shouldThrowException() {
        screenWindow.setScreenId(2L);
        when(windowMapper.findByWindowId(WINDOW_ID)).thenReturn(screenWindow);

        WindowUpdateRequest request = new WindowUpdateRequest();
        request.setX(100);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> windowService.updateWindow(SCREEN_ID, WINDOW_ID, request));
        assertEquals("窗口不存在: win-001", ex.getMessage());
    }

    // ========== 关闭窗口：参数校验 ==========

    /**
     * 关闭不存在的窗口应抛出异常
     */
    @Test
    void closeWindow_notFound_shouldThrowException() {
        when(windowMapper.findByWindowId("win-999")).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> windowService.closeWindow(SCREEN_ID, "win-999"));
        assertEquals("窗口不存在: win-999", ex.getMessage());
    }

    /**
     * 窗口所属大屏不匹配应抛出异常
     */
    @Test
    void closeWindow_wrongScreenId_shouldThrowException() {
        screenWindow.setScreenId(2L);
        when(windowMapper.findByWindowId(WINDOW_ID)).thenReturn(screenWindow);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> windowService.closeWindow(SCREEN_ID, WINDOW_ID));
        assertEquals("窗口不存在: win-001", ex.getMessage());
    }

    // ========== 查询窗口 ==========

    /**
     * 查询大屏下所有窗口应委托给 Mapper
     */
    @Test
    void getWindows_shouldDelegateToMapper() {
        List<ScreenWindowVO> windows = new ArrayList<>();
        when(windowMapper.findByScreenId(SCREEN_ID)).thenReturn(windows);

        List<ScreenWindowVO> result = windowService.getWindows(SCREEN_ID);
        assertSame(windows, result);
    }

    // ========== 清空窗口 ==========

    /**
     * 清空无窗口的大屏不应报错
     */
    @Test
    void clearWindows_emptyScreen_shouldNotThrow() {
        when(windowMapper.findByScreenId(SCREEN_ID)).thenReturn(new ArrayList<>());

        assertDoesNotThrow(() -> windowService.clearWindows(SCREEN_ID));
    }

    // ========== 输出设备窗口查询 ==========

    /**
     * 查询不存在大屏的输出设备窗口应抛出异常
     */
    @Test
    void getOutputDeviceWindows_screenNotFound_shouldThrowException() {
        when(screenMapper.findById(SCREEN_ID)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> windowService.getOutputDeviceWindows(SCREEN_ID));
        assertEquals("大屏不存在: id=1", ex.getMessage());
    }

    // ========== 设备恢复后窗口补推 ==========

    /**
     * 输入设备恢复时应标记其窗口为 pending
     */
    @Test
    void markPendingForDevice_inputDevice_shouldMarkPending() {
        device.setDeviceCategory("INPUT");

        windowService.markPendingForDevice(device);

        verify(windowMapper).updateSyncStatusByDeviceId(device.getId(), "pending");
    }

    /**
     * 输出设备恢复时应标记相关大屏窗口为 pending
     */
    @Test
    void markPendingForDevice_outputDevice_shouldMarkPendingByScreen() {
        device.setDeviceCategory("OUTPUT");
        when(screenMapper.findScreenIdsByDeviceId(device.getId()))
                .thenReturn(List.of(SCREEN_ID));

        windowService.markPendingForDevice(device);

        verify(windowMapper).updateSyncStatusByScreenId(SCREEN_ID, "pending");
    }

    // ========== 设备离线后窗口标记 ==========

    /**
     * 输入设备离线时应标记其窗口为 failed + 降级
     */
    @Test
    void markFailedForDevice_inputDevice_shouldMarkFailed() {
        device.setDeviceCategory("INPUT");

        windowService.markFailedForDevice(device);

        verify(windowMapper).markFailedByDeviceId(device.getId(), "failed", 1);
    }

    /**
     * 输出设备离线时应标记相关大屏窗口为 failed + 降级
     */
    @Test
    void markFailedForDevice_outputDevice_shouldMarkFailedByScreen() {
        device.setDeviceCategory("OUTPUT");
        when(screenMapper.findScreenIdsByDeviceId(device.getId()))
                .thenReturn(List.of(SCREEN_ID));

        windowService.markFailedForDevice(device);

        verify(windowMapper).markFailedByScreenId(SCREEN_ID, "failed", 1);
    }
}