package com.example.demo.service.impl;

import com.example.demo.common.*;
import com.example.demo.driver.DeviceDriver;
import com.example.demo.driver.DeviceEndpoint;
import com.example.demo.entity.Screen;
import com.example.demo.entity.ScreenWindow;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.mapper.ScreenMapper;
import com.example.demo.mapper.WindowMapper;
import com.example.demo.model.SimWindow;
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

    /**
     * 窗口覆盖的通道窗口数已达上限应抛出异常
     */
    @Test
    void createWindow_maxWindowsExceeded_shouldThrowException() {
        when(screenMapper.findById(SCREEN_ID)).thenReturn(screen);

        CellVO cell = new CellVO();
        cell.setDeviceId(1L);
        cell.setChannelName("OUT-1");
        cell.setDeviceName("Dev1");
        cell.setRowIndex(0);
        cell.setColIndex(0);
        cell.setMaxWindows(1);
        when(screenMapper.findCellsByScreenId(SCREEN_ID)).thenReturn(List.of(cell));

        ScreenWindowVO existing = new ScreenWindowVO();
        existing.setWindowId("win-existing");
        existing.setX(0);
        existing.setY(0);
        existing.setWidth(960);
        existing.setHeight(540);
        when(windowMapper.findByScreenId(SCREEN_ID)).thenReturn(List.of(existing));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> windowService.createWindow(SCREEN_ID, createRequest));
        assertTrue(ex.getMessage().contains("上限"));
    }

    /**
     * 窗口在不支持叠加的设备上与新窗口重叠应抛出异常
     */
    @Test
    void createWindow_overlayNotSupported_shouldThrowException() {
        when(screenMapper.findById(SCREEN_ID)).thenReturn(screen);

        CellVO cell = new CellVO();
        cell.setDeviceId(1L);
        cell.setChannelName("OUT-1");
        cell.setDeviceName("Dev1");
        cell.setRowIndex(0);
        cell.setColIndex(0);
        cell.setSupportOverlay(0);
        when(screenMapper.findCellsByScreenId(SCREEN_ID)).thenReturn(List.of(cell));

        ScreenWindowVO existing = new ScreenWindowVO();
        existing.setWindowId("win-existing");
        existing.setX(0);
        existing.setY(0);
        existing.setWidth(960);
        existing.setHeight(540);
        when(windowMapper.findByScreenId(SCREEN_ID)).thenReturn(List.of(existing));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> windowService.createWindow(SCREEN_ID, createRequest));
        assertTrue(ex.getMessage().contains("不支持窗口叠加"));
    }

    /**
     * 正常创建窗口应成功写入 DB 并同步到设备
     */
    @Test
    void createWindow_valid_shouldSucceed() {
        when(screenMapper.findById(SCREEN_ID)).thenReturn(screen);

        CellVO cell = new CellVO();
        cell.setDeviceId(1L);
        cell.setChannelName("OUT-1");
        cell.setDeviceName("Dev1");
        cell.setRowIndex(0);
        cell.setColIndex(0);
        cell.setMaxWindows(4);
        cell.setSupportOverlay(1);
        cell.setSupportMove(1);
        cell.setSupportResize(1);
        when(screenMapper.findCellsByScreenId(SCREEN_ID)).thenReturn(List.of(cell));
        when(windowMapper.findByScreenId(SCREEN_ID)).thenReturn(new ArrayList<>());

        when(deviceMapper.findById(1L)).thenReturn(device);

        Result<SimWindow> result = Result.success();
        when(deviceDriver.createWindow(any(DeviceEndpoint.class), any(SimWindow.class))).thenReturn(result);

        ScreenWindow dbWindow = new ScreenWindow();
        dbWindow.setWindowId(WINDOW_ID);
        dbWindow.setScreenId(SCREEN_ID);
        dbWindow.setDeviceId(1L);
        dbWindow.setChannelName("OUT-1");
        dbWindow.setX(0);
        dbWindow.setY(0);
        dbWindow.setWidth(960);
        dbWindow.setHeight(540);
        when(windowMapper.findByWindowId(WINDOW_ID)).thenReturn(dbWindow);

        ScreenWindowVO resultVO = windowService.createWindow(SCREEN_ID, createRequest);
        assertNotNull(resultVO);
        assertEquals(WINDOW_ID, resultVO.getWindowId());

        verify(windowMapper).insert(any(ScreenWindow.class));
        verify(deviceDriver).createWindow(any(DeviceEndpoint.class), any(SimWindow.class));
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

    /**
     * 更新窗口移动时，目标通道不支持移动应回退到原位置（不报错）
     */
    @Test
    void updateWindow_moveNotSupported_shouldRollback() {
        when(windowMapper.findByWindowId(WINDOW_ID)).thenReturn(screenWindow);
        when(screenMapper.findById(SCREEN_ID)).thenReturn(screen);

        CellVO cell = new CellVO();
        cell.setDeviceId(1L);
        cell.setChannelName("OUT-1");
        cell.setDeviceName("Dev1");
        cell.setRowIndex(0);
        cell.setColIndex(0);
        cell.setSupportMove(0); // 不支持移动
        cell.setSupportResize(1);
        cell.setSupportOverlay(1);
        cell.setMaxWindows(4);
        when(screenMapper.findCellsByScreenId(SCREEN_ID)).thenReturn(List.of(cell));

        when(deviceMapper.findById(1L)).thenReturn(device);
        when(windowMapper.findByWindowId(WINDOW_ID)).thenReturn(screenWindow);

        WindowUpdateRequest request = new WindowUpdateRequest();
        request.setX(100);
        request.setY(200);

        ScreenWindowVO result = windowService.updateWindow(SCREEN_ID, WINDOW_ID, request);
        assertNotNull(result);
        // 不应触发 updatePosition（因为能力不支持，直接回退）
        verify(windowMapper, never()).updatePosition(anyString(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    /**
     * 更新窗口缩放时，目标通道不支持缩放应回退到原尺寸
     */
    @Test
    void updateWindow_resizeNotSupported_shouldRollback() {
        when(windowMapper.findByWindowId(WINDOW_ID)).thenReturn(screenWindow);
        when(screenMapper.findById(SCREEN_ID)).thenReturn(screen);

        CellVO cell = new CellVO();
        cell.setDeviceId(1L);
        cell.setChannelName("OUT-1");
        cell.setDeviceName("Dev1");
        cell.setRowIndex(0);
        cell.setColIndex(0);
        cell.setSupportMove(1);
        cell.setSupportResize(0); // 不支持缩放
        cell.setSupportOverlay(1);
        cell.setMaxWindows(4);
        when(screenMapper.findCellsByScreenId(SCREEN_ID)).thenReturn(List.of(cell));

        when(deviceMapper.findById(1L)).thenReturn(device);
        when(windowMapper.findByWindowId(WINDOW_ID)).thenReturn(screenWindow);

        WindowUpdateRequest request = new WindowUpdateRequest();
        request.setWidth(1920);
        request.setHeight(1080);

        ScreenWindowVO result = windowService.updateWindow(SCREEN_ID, WINDOW_ID, request);
        assertNotNull(result);
        verify(windowMapper, never()).updatePosition(anyString(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    /**
     * 更新窗口时，在不支持叠加的设备上产生新重叠应抛出异常
     */
    @Test
    void updateWindow_overlayNotSupported_shouldThrowException() {
        // 先把窗口位置改到新位置，触发叠加校验
        screenWindow.setX(100);
        screenWindow.setY(100);
        when(windowMapper.findByWindowId(WINDOW_ID)).thenReturn(screenWindow);
        when(screenMapper.findById(SCREEN_ID)).thenReturn(screen);

        CellVO cell = new CellVO();
        cell.setDeviceId(1L);
        cell.setChannelName("OUT-1");
        cell.setDeviceName("Dev1");
        cell.setRowIndex(0);
        cell.setColIndex(0);
        cell.setSupportMove(1);
        cell.setSupportResize(1);
        cell.setSupportOverlay(0); // 不支持叠加
        cell.setMaxWindows(4);
        when(screenMapper.findCellsByScreenId(SCREEN_ID)).thenReturn(List.of(cell));

        // 已有一个窗口覆盖同一单元
        ScreenWindowVO existing = new ScreenWindowVO();
        existing.setWindowId("win-existing");
        existing.setX(0);
        existing.setY(0);
        existing.setWidth(1920);
        existing.setHeight(1080);
        when(windowMapper.findByScreenId(SCREEN_ID)).thenReturn(List.of(existing));

        WindowUpdateRequest request = new WindowUpdateRequest();
        request.setX(100);
        request.setY(100);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> windowService.updateWindow(SCREEN_ID, WINDOW_ID, request));
        assertTrue(ex.getMessage().contains("不支持窗口叠加"));
    }

    /**
     * 正常更新窗口位置应成功
     */
    @Test
    void updateWindow_valid_shouldSucceed() {
        when(windowMapper.findByWindowId(WINDOW_ID)).thenReturn(screenWindow);
        when(screenMapper.findById(SCREEN_ID)).thenReturn(screen);

        CellVO cell = new CellVO();
        cell.setDeviceId(1L);
        cell.setChannelName("OUT-1");
        cell.setDeviceName("Dev1");
        cell.setRowIndex(0);
        cell.setColIndex(0);
        cell.setSupportMove(1);
        cell.setSupportResize(1);
        cell.setSupportOverlay(1);
        cell.setMaxWindows(4);
        when(screenMapper.findCellsByScreenId(SCREEN_ID)).thenReturn(List.of(cell));
        when(windowMapper.findByScreenId(SCREEN_ID)).thenReturn(new ArrayList<>());

        when(deviceMapper.findById(1L)).thenReturn(device);
        when(windowMapper.findByWindowId(WINDOW_ID)).thenReturn(screenWindow);

        Result<SimWindow> result = Result.success();
        when(deviceDriver.createWindow(any(DeviceEndpoint.class), any(SimWindow.class))).thenReturn(result);

        WindowUpdateRequest request = new WindowUpdateRequest();
        request.setX(100);
        request.setY(200);

        ScreenWindowVO resultVO = windowService.updateWindow(SCREEN_ID, WINDOW_ID, request);
        assertNotNull(resultVO);
        assertEquals(WINDOW_ID, resultVO.getWindowId());

        verify(windowMapper).updatePosition(WINDOW_ID, 100, 200, 960, 540);
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

    /**
     * 正常关闭窗口应先标记 closing，再通知设备清理，最后删 DB
     */
    @Test
    void closeWindow_valid_shouldCloseAndDelete() {
        when(windowMapper.findByWindowId(WINDOW_ID)).thenReturn(screenWindow);

        CellVO cell = new CellVO();
        cell.setDeviceId(1L);
        cell.setChannelName("OUT-1");
        cell.setRowIndex(0);
        cell.setColIndex(0);
        when(screenMapper.findCellsByScreenId(SCREEN_ID)).thenReturn(List.of(cell));

        when(deviceMapper.findById(1L)).thenReturn(device);

        windowService.closeWindow(SCREEN_ID, WINDOW_ID);

        verify(windowMapper).updateSyncStatus(WINDOW_ID, "closing");
        verify(deviceDriver).closeWindow(any(DeviceEndpoint.class), eq(WINDOW_ID));
        verify(windowMapper).deleteByWindowId(WINDOW_ID);
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

    /**
     * 清空有窗口的大屏应逐个关闭所有窗口
     */
    @Test
    void clearWindows_withWindows_shouldCloseAll() {
        ScreenWindowVO win1 = new ScreenWindowVO();
        win1.setWindowId("win-001");
        ScreenWindowVO win2 = new ScreenWindowVO();
        win2.setWindowId("win-002");
        when(windowMapper.findByScreenId(SCREEN_ID)).thenReturn(List.of(win1, win2));

        when(windowMapper.findByWindowId("win-001")).thenReturn(screenWindow);
        when(windowMapper.findByWindowId("win-002")).thenReturn(screenWindow);

        CellVO cell = new CellVO();
        cell.setDeviceId(1L);
        cell.setChannelName("OUT-1");
        cell.setRowIndex(0);
        cell.setColIndex(0);
        when(screenMapper.findCellsByScreenId(SCREEN_ID)).thenReturn(List.of(cell));

        when(deviceMapper.findById(1L)).thenReturn(device);

        windowService.clearWindows(SCREEN_ID);

        verify(windowMapper).updateSyncStatus("win-001", "closing");
        verify(windowMapper).updateSyncStatus("win-002", "closing");
        verify(windowMapper, times(2)).deleteByWindowId(anyString());
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

    /**
     * 正常查询应返回按设备分组的子窗口信息
     */
    @Test
    void getOutputDeviceWindows_valid_shouldReturnDeviceWindows() {
        when(screenMapper.findById(SCREEN_ID)).thenReturn(screen);

        CellVO cell = new CellVO();
        cell.setId(10L);
        cell.setDeviceId(1L);
        cell.setDeviceName("Dev1");
        cell.setChannelName("OUT-1");
        cell.setRowIndex(0);
        cell.setColIndex(0);
        cell.setOnline(1);
        cell.setBaseUrl("http://192.168.1.100:8086");
        cell.setMaxWindows(4);
        cell.setSupportMove(1);
        cell.setSupportResize(1);
        cell.setSupportOverlay(1);
        cell.setMaxResolution("1920x1080");
        when(screenMapper.findCellsByScreenId(SCREEN_ID)).thenReturn(List.of(cell));

        ScreenWindowVO win = new ScreenWindowVO();
        win.setWindowId(WINDOW_ID);
        win.setDeviceName("Dev1");
        win.setChannelName("OUT-1");
        win.setX(0);
        win.setY(0);
        win.setWidth(960);
        win.setHeight(540);
        win.setSourceType("HDMI");
        win.setSourceUrl("rtsp://cam1/stream");
        win.setSyncStatus("synced");
        win.setDegraded(0);
        when(windowMapper.findByScreenId(SCREEN_ID)).thenReturn(List.of(win));

        List<OutputDeviceWindowsVO> result = windowService.getOutputDeviceWindows(SCREEN_ID);
        assertNotNull(result);
        assertEquals(1, result.size());

        OutputDeviceWindowsVO deviceWin = result.get(0);
        assertEquals(10L, deviceWin.getCellId());
        assertEquals("Dev1", deviceWin.getDeviceName());
        assertEquals(1, deviceWin.getWindows().size());

        SubWindowVO sub = deviceWin.getWindows().get(0);
        assertEquals(WINDOW_ID, sub.getWindowId());
        assertEquals(0, sub.getX());
        assertEquals(0, sub.getY());
        assertEquals(960, sub.getWidth());
        assertEquals(540, sub.getHeight());
        assertEquals("HDMI", sub.getSourceType());
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
     * 输出设备离线时只应标记覆盖了该设备单元的窗口，而非整个大屏的所有窗口。
     * 这是修复的核心：避免一个设备离线影响同大屏上不相关的窗口。
     */
    @Test
    void markFailedForDevice_outputDevice_shouldOnlyMarkWindowsCoveringItsCells() {
        device.setDeviceCategory("OUTPUT");
        when(screenMapper.findScreenIdsByDeviceId(device.getId()))
                .thenReturn(List.of(SCREEN_ID));
        when(screenMapper.findById(SCREEN_ID)).thenReturn(screen);

        // 单元 [0,0] 绑定到此输出设备
        CellVO cell = new CellVO();
        cell.setDeviceId(device.getId());
        cell.setRowIndex(0);
        cell.setColIndex(0);
        when(screenMapper.findCellsByScreenId(SCREEN_ID))
                .thenReturn(List.of(cell));

        // 窗口覆盖单元 [0,0]（x=0,y=0,w=960,h=540，在 1920×1080 单元内，矩形相交）
        ScreenWindowVO win = new ScreenWindowVO();
        win.setWindowId(WINDOW_ID);
        win.setSyncStatus("synced");
        win.setX(0);
        win.setY(0);
        win.setWidth(960);
        win.setHeight(540);
        when(windowMapper.findByScreenId(SCREEN_ID))
                .thenReturn(List.of(win));

        windowService.markFailedForDevice(device);

        // 只标记了真正覆盖该设备单元的窗口
        verify(windowMapper).updateDegraded(WINDOW_ID, "failed", 1);
        // 不再调用全屏标记
        verify(windowMapper, never()).markFailedByScreenId(anyLong(), anyString(), anyInt());
    }

    /**
     * 输出设备离线时，不覆盖该设备的窗口不应被标记
     */
    @Test
    void markFailedForDevice_outputDevice_shouldNotMarkWindowsOutsideItsCells() {
        device.setDeviceCategory("OUTPUT");
        when(screenMapper.findScreenIdsByDeviceId(device.getId()))
                .thenReturn(List.of(SCREEN_ID));
        when(screenMapper.findById(SCREEN_ID)).thenReturn(screen);

        // 单元 [1,1] 绑定到此输出设备
        CellVO cell = new CellVO();
        cell.setDeviceId(device.getId());
        cell.setRowIndex(1);
        cell.setColIndex(1);
        when(screenMapper.findCellsByScreenId(SCREEN_ID))
                .thenReturn(List.of(cell));

        // 窗口在单元 [0,0]（不覆盖单元 [1,1]）
        ScreenWindowVO win = new ScreenWindowVO();
        win.setWindowId(WINDOW_ID);
        win.setSyncStatus("synced");
        win.setX(0);
        win.setY(0);
        win.setWidth(960);
        win.setHeight(540);
        when(windowMapper.findByScreenId(SCREEN_ID))
                .thenReturn(List.of(win));

        windowService.markFailedForDevice(device);

        // 窗口不覆盖该设备的单元，不应被标记
        verify(windowMapper, never()).updateDegraded(anyString(), anyString(), anyInt());
    }
}