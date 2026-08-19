package com.example.demo.service.impl;

import com.example.demo.common.*;
import com.example.demo.entity.Screen;
import com.example.demo.entity.ScreenCell;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.mapper.ScreenMapper;
import com.github.pagehelper.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ScreenServiceImpl 单元测试
 * <p>
 * 覆盖大屏创建、分页查询、详情查询、删除、单元绑定的完整流程，
 * 重点验证绑定校验（设备状态、通道有效性、分辨率匹配、通道超限、重复绑定）。
 */
@ExtendWith(MockitoExtension.class)
class ScreenServiceImplTest {

    @Mock
    private ScreenMapper screenMapper;

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private WindowServiceImpl windowService;

    @InjectMocks
    private ScreenServiceImpl screenService;

    private DevicePageVO outputDevice;
    private ScreenCreateRequest createRequest;
    private Screen screen;

    @BeforeEach
    void setUp() {
        outputDevice = new DevicePageVO();
        outputDevice.setId(1L);
        outputDevice.setDeviceName("REST-Output-01");
        outputDevice.setDeviceType("REST");
        outputDevice.setDeviceCategory("OUTPUT");
        outputDevice.setEnabled(1);
        outputDevice.setOnline(1);
        outputDevice.setMaxResolution("1920x1080");
        outputDevice.setOutputChannel1("OUT-1");
        outputDevice.setOutputChannel2("OUT-2");
        outputDevice.setOutputChannel3("OUT-3");

        screen = new Screen();
        screen.setId(1L);
        screen.setScreenName("TestScreen");
        screen.setRowsCount(1);
        screen.setColsCount(1);
        screen.setCellWidth(1920);
        screen.setCellHeight(1080);
        screen.setCreateTime(LocalDateTime.now());
    }

    private ScreenCreateRequest buildCreateRequest() {
        ScreenCreateRequest req = new ScreenCreateRequest();
        req.setScreenName("TestScreen");
        req.setRowsCount(1);
        req.setColsCount(1);
        req.setCellWidth(1920);
        req.setCellHeight(1080);

        CellBindRequest bind = new CellBindRequest();
        bind.setRowIndex(0);
        bind.setColIndex(0);
        bind.setDeviceId(1L);
        bind.setChannelName("OUT-1");
        req.setCells(List.of(bind));
        return req;
    }

    // ========== 创建大屏：参数校验 ==========

    /**
     * 大屏名称为空应抛出异常
     */
    @Test
    void createScreen_emptyName_shouldThrowException() {
        createRequest = buildCreateRequest();
        createRequest.setScreenName("");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> screenService.createScreen(createRequest));
        assertEquals("大屏名称不能为空", ex.getMessage());
    }

    /**
     * 大屏名称重复应抛出异常
     */
    @Test
    void createScreen_duplicateName_shouldThrowException() {
        createRequest = buildCreateRequest();
        when(screenMapper.countByScreenName("TestScreen")).thenReturn(1);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> screenService.createScreen(createRequest));
        assertEquals("大屏名称已存在: TestScreen", ex.getMessage());
    }

    /**
     * 绑定数量与单元数不匹配应抛出异常（每个单元必须绑定）
     */
    @Test
    void createScreen_bindingCountMismatch_shouldThrowException() {
        createRequest = buildCreateRequest();
        createRequest.setRowsCount(2);
        createRequest.setColsCount(2);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> screenService.createScreen(createRequest));
        assertTrue(ex.getMessage().contains("每个单元必须绑定设备"));
    }

    // ========== 创建大屏：设备校验 ==========

    /**
     * 绑定不存在的设备应抛出异常
     */
    @Test
    void createScreen_deviceNotFound_shouldThrowException() {
        createRequest = buildCreateRequest();
        when(screenMapper.countByScreenName("TestScreen")).thenReturn(0);
        when(deviceMapper.findById(1L)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> screenService.createScreen(createRequest));
        assertEquals("设备不存在: 1", ex.getMessage());
    }

    /**
     * 禁止绑定已禁用的设备
     */
    @Test
    void createScreen_deviceDisabled_shouldThrowException() {
        createRequest = buildCreateRequest();
        outputDevice.setEnabled(0);
        when(screenMapper.countByScreenName("TestScreen")).thenReturn(0);
        when(deviceMapper.findById(1L)).thenReturn(outputDevice);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> screenService.createScreen(createRequest));
        assertEquals("设备 REST-Output-01 已禁用，不允许绑定", ex.getMessage());
    }

    /**
     * 禁止绑定离线设备
     */
    @Test
    void createScreen_deviceOffline_shouldThrowException() {
        createRequest = buildCreateRequest();
        outputDevice.setOnline(0);
        when(screenMapper.countByScreenName("TestScreen")).thenReturn(0);
        when(deviceMapper.findById(1L)).thenReturn(outputDevice);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> screenService.createScreen(createRequest));
        assertEquals("设备 REST-Output-01 当前离线，不允许绑定", ex.getMessage());
    }

    /**
     * 禁止绑定输入设备（大屏只能绑定输出设备）
     */
    @Test
    void createScreen_inputDevice_shouldThrowException() {
        createRequest = buildCreateRequest();
        outputDevice.setDeviceCategory("INPUT");
        when(screenMapper.countByScreenName("TestScreen")).thenReturn(0);
        when(deviceMapper.findById(1L)).thenReturn(outputDevice);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> screenService.createScreen(createRequest));
        assertEquals("该设备为输入设备，不可用于大屏绑定", ex.getMessage());
    }

    // ========== 创建大屏：通道校验 ==========

    /**
     * 绑定不存在的通道应抛出异常
     */
    @Test
    void createScreen_invalidChannel_shouldThrowException() {
        createRequest = buildCreateRequest();
        createRequest.getCells().get(0).setChannelName("INVALID_CH");
        when(screenMapper.countByScreenName("TestScreen")).thenReturn(0);
        when(deviceMapper.findById(1L)).thenReturn(outputDevice);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> screenService.createScreen(createRequest));
        assertEquals("通道名无效: INVALID_CH", ex.getMessage());
    }

    /**
     * 单元分辨率与设备最大分辨率不匹配应抛出异常
     */
    @Test
    void createScreen_resolutionMismatch_shouldThrowException() {
        createRequest = buildCreateRequest();
        createRequest.setCellWidth(1280);
        createRequest.setCellHeight(720);
        outputDevice.setMaxResolution("1920x1080");
        when(screenMapper.countByScreenName("TestScreen")).thenReturn(0);
        when(deviceMapper.findById(1L)).thenReturn(outputDevice);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> screenService.createScreen(createRequest));
        assertTrue(ex.getMessage().contains("不匹配大屏单元分辨率"));
    }

    /**
     * 同一请求中同一通道不能绑定到多个单元
     */
    @Test
    void createScreen_duplicateChannelInSameRequest_shouldThrowException() {
        createRequest = buildCreateRequest();
        createRequest.setRowsCount(2);
        createRequest.setColsCount(1);

        CellBindRequest bind1 = new CellBindRequest();
        bind1.setRowIndex(0);
        bind1.setColIndex(0);
        bind1.setDeviceId(1L);
        bind1.setChannelName("OUT-1");

        CellBindRequest bind2 = new CellBindRequest();
        bind2.setRowIndex(1);
        bind2.setColIndex(0);
        bind2.setDeviceId(1L);
        bind2.setChannelName("OUT-1");

        createRequest.setCells(List.of(bind1, bind2));
        when(screenMapper.countByScreenName("TestScreen")).thenReturn(0);
        when(deviceMapper.findById(1L)).thenReturn(outputDevice);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> screenService.createScreen(createRequest));
        assertTrue(ex.getMessage().contains("不能同时绑定到多个单元"));
    }

    /**
     * 设备输出通道全部被占用时应抛出异常（通道超限）
     */
    @Test
    void createScreen_exceedChannelLimit_shouldThrowException() {
        createRequest = buildCreateRequest();
        createRequest.setRowsCount(2);
        createRequest.setColsCount(1);

        CellBindRequest bind1 = new CellBindRequest();
        bind1.setRowIndex(0);
        bind1.setColIndex(0);
        bind1.setDeviceId(1L);
        bind1.setChannelName("OUT-1");

        CellBindRequest bind2 = new CellBindRequest();
        bind2.setRowIndex(1);
        bind2.setColIndex(0);
        bind2.setDeviceId(1L);
        bind2.setChannelName("OUT-2");

        createRequest.setCells(List.of(bind1, bind2));
        when(screenMapper.countByScreenName("TestScreen")).thenReturn(0);
        when(deviceMapper.findById(1L)).thenReturn(outputDevice);
        when(screenMapper.countDeviceBindings(1L)).thenReturn(2);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> screenService.createScreen(createRequest));
        assertTrue(ex.getMessage().contains("输出通道已全部被占用"));
    }

    // ========== 创建大屏：正常流程 ==========

    /**
     * 正常创建大屏应返回完整详情
     */
    @Test
    void createScreen_success_shouldReturnDetail() {
        createRequest = buildCreateRequest();
        when(screenMapper.countByScreenName("TestScreen")).thenReturn(0);
        when(deviceMapper.findById(1L)).thenReturn(outputDevice);
        when(screenMapper.countDeviceBindings(1L)).thenReturn(0);
        doAnswer(inv -> {
            Screen s = inv.getArgument(0);
            s.setId(1L);
            return 1;
        }).when(screenMapper).insertScreen(any(Screen.class));
        when(screenMapper.findCellsByScreenId(1L)).thenReturn(new ArrayList<>());

        ScreenDetailVO result = screenService.createScreen(createRequest);

        assertNotNull(result);
        assertEquals("TestScreen", result.getScreenName());
        assertEquals(1, result.getRowsCount());
        assertEquals(1, result.getColsCount());
    }

    // ========== 大屏分页查询 ==========

    /**
     * 分页查询应返回正确的分页结果
     */
    @SuppressWarnings("unchecked")
    @Test
    void getPage_shouldReturnPageResult() {
        ScreenPageDTO dto = new ScreenPageDTO();
        dto.setPage(1);
        dto.setPageSize(10);

        Page<ScreenPageVO> mockPage = mock(Page.class);
        when(mockPage.getTotal()).thenReturn(0L);
        when(mockPage.getResult()).thenReturn(new ArrayList<>());
        when(screenMapper.pageQuery(dto)).thenReturn(mockPage);

        PageResult<ScreenPageVO> result = screenService.getPage(dto);
        assertEquals(0L, result.getTotal());
    }

    // ========== 大屏详情查询 ==========

    /**
     * 查询不存在的大屏应抛出异常
     */
    @Test
    void getDetail_notFound_shouldThrowException() {
        when(screenMapper.findById(999L)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> screenService.getDetail(999L));
        assertEquals("大屏不存在: id=999", ex.getMessage());
    }

    /**
     * 正常查询大屏详情
     */
    @Test
    void getDetail_success_shouldReturnDetail() {
        when(screenMapper.findById(1L)).thenReturn(screen);
        when(screenMapper.findCellsByScreenId(1L)).thenReturn(new ArrayList<>());

        ScreenDetailVO result = screenService.getDetail(1L);
        assertNotNull(result);
        assertEquals("TestScreen", result.getScreenName());
    }

    // ========== 大屏删除 ==========

    /**
     * 删除不存在的大屏应抛出异常
     */
    @Test
    void deleteScreen_notFound_shouldThrowException() {
        when(screenMapper.findById(999L)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> screenService.deleteScreen(999L));
        assertEquals("大屏不存在: id=999", ex.getMessage());
    }

    /**
     * 正常删除大屏
     */
    @Test
    void deleteScreen_success_shouldDelete() {
        when(screenMapper.findById(1L)).thenReturn(screen);

        screenService.deleteScreen(1L);
        verify(screenMapper).deleteScreen(1L);
    }

    // ========== 单元绑定 ==========

    /**
     * 绑定不存在的单元应抛出异常
     */
    @Test
    void bindCell_cellNotFound_shouldThrowException() {
        when(screenMapper.findCellById(999L)).thenReturn(null);

        CellBindRequest request = new CellBindRequest();
        request.setDeviceId(1L);
        request.setChannelName("OUT-1");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> screenService.bindCell(1L, 999L, request));
        assertEquals("单元不存在", ex.getMessage());
    }

    /**
     * 不允许解绑（deviceId 为 null 表示解绑操作）
     */
    @Test
    void bindCell_nullDeviceId_shouldThrowException() {
        ScreenCell cell = new ScreenCell();
        cell.setId(1L);
        cell.setScreenId(1L);
        when(screenMapper.findCellById(1L)).thenReturn(cell);

        CellBindRequest request = new CellBindRequest();
        request.setDeviceId(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> screenService.bindCell(1L, 1L, request));
        assertEquals("不允许解绑，只能更换绑定设备", ex.getMessage());
    }

    /**
     * 正常更换绑定应返回单元 VO
     */
    @Test
    void bindCell_success_shouldReturnCellVO() {
        ScreenCell cell = new ScreenCell();
        cell.setId(1L);
        cell.setScreenId(1L);
        cell.setRowIndex(0);
        cell.setColIndex(0);
        cell.setDeviceId(2L);
        cell.setChannelName("OUT-2");

        when(screenMapper.findCellById(1L)).thenReturn(cell);
        when(screenMapper.findById(1L)).thenReturn(screen);
        when(deviceMapper.findById(1L)).thenReturn(outputDevice);
        when(screenMapper.countDeviceBindings(1L)).thenReturn(0);

        CellBindRequest request = new CellBindRequest();
        request.setDeviceId(1L);
        request.setChannelName("OUT-1");

        CellVO result = screenService.bindCell(1L, 1L, request);

        assertNotNull(result);
        verify(screenMapper).updateCellBinding(1L, 1L, "OUT-1");
    }

    /**
     * 绑定到已禁用的设备应抛出异常
     */
    @Test
    void bindCell_deviceDisabled_shouldThrowException() {
        ScreenCell cell = new ScreenCell();
        cell.setId(1L);
        cell.setScreenId(1L);
        cell.setDeviceId(2L);
        cell.setChannelName("OUT-2");

        when(screenMapper.findCellById(1L)).thenReturn(cell);
        when(screenMapper.findById(1L)).thenReturn(screen);
        outputDevice.setEnabled(0);
        when(deviceMapper.findById(1L)).thenReturn(outputDevice);

        CellBindRequest request = new CellBindRequest();
        request.setDeviceId(1L);
        request.setChannelName("OUT-1");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> screenService.bindCell(1L, 1L, request));
        assertEquals("设备 REST-Output-01 已禁用，不允许绑定", ex.getMessage());
    }

    /**
     * 绑定到不存在的通道应抛出异常
     */
    @Test
    void bindCell_invalidChannel_shouldThrowException() {
        ScreenCell cell = new ScreenCell();
        cell.setId(1L);
        cell.setScreenId(1L);
        cell.setDeviceId(2L);
        cell.setChannelName("OUT-2");

        when(screenMapper.findCellById(1L)).thenReturn(cell);
        when(screenMapper.findById(1L)).thenReturn(screen);
        when(deviceMapper.findById(1L)).thenReturn(outputDevice);

        CellBindRequest request = new CellBindRequest();
        request.setDeviceId(1L);
        request.setChannelName("INVALID_CH");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> screenService.bindCell(1L, 1L, request));
        assertEquals("通道名无效: INVALID_CH", ex.getMessage());
    }

    /**
     * 新设备分辨率与单元不匹配应抛出异常
     */
    @Test
    void bindCell_resolutionMismatch_shouldThrowException() {
        ScreenCell cell = new ScreenCell();
        cell.setId(1L);
        cell.setScreenId(1L);
        cell.setDeviceId(2L);
        cell.setChannelName("OUT-2");

        when(screenMapper.findCellById(1L)).thenReturn(cell);
        screen.setCellWidth(1280);
        screen.setCellHeight(720);
        when(screenMapper.findById(1L)).thenReturn(screen);
        when(deviceMapper.findById(1L)).thenReturn(outputDevice);

        CellBindRequest request = new CellBindRequest();
        request.setDeviceId(1L);
        request.setChannelName("OUT-1");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> screenService.bindCell(1L, 1L, request));
        assertTrue(ex.getMessage().contains("不匹配大屏单元分辨率"));
    }
}