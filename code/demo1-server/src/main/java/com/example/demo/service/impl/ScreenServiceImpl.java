package com.example.demo.service.impl;

import com.example.demo.common.*;
import com.example.demo.entity.Screen;
import com.example.demo.entity.ScreenCell;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.mapper.ScreenMapper;
import com.example.demo.service.ScreenService;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 大屏配置 Service 实现
 * <p>
 * 负责大屏的创建、查询、删除、单元绑定等操作。
 * 创建大屏时自动生成 rows×cols 个单元，每个单元必须绑定一个输出设备的通道。
 */
@Service
public class ScreenServiceImpl implements ScreenService {

    @Autowired
    private ScreenMapper screenMapper;

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private WindowServiceImpl windowService;

    /**
     * 创建大屏
     * <p>
     * 校验名称唯一性、单元绑定合法性（设备有效性、通道可用性、分辨率匹配），
     * 自动生成 rows×cols 个单元并写入数据库。
     *
     * @param request 大屏参数（名称、行列数、单元尺寸、每个单元的绑定信息）
     * @return 大屏详情（含单元列表）
     */
    @Override
    @Transactional
    public ScreenDetailVO createScreen(ScreenCreateRequest request) {
        if (!StringUtils.hasText(request.getScreenName())) {
            throw new RuntimeException("大屏名称不能为空");
        }
        if (screenMapper.countByScreenName(request.getScreenName()) > 0) {
            throw new RuntimeException("大屏名称已存在: " + request.getScreenName());
        }
        if (request.getRowsCount() == null || request.getRowsCount() < 1) request.setRowsCount(1);
        if (request.getColsCount() == null || request.getColsCount() < 1) request.setColsCount(1);
        if (request.getCellWidth() == null) request.setCellWidth(1920);
        if (request.getCellHeight() == null) request.setCellHeight(1080);

        int totalCells = request.getRowsCount() * request.getColsCount();
        List<CellBindRequest> bindings = request.getCells();
        if (bindings == null || bindings.size() != totalCells) {
            throw new RuntimeException("每个单元必须绑定设备，需要 " + totalCells + " 个绑定，实际提供 " + (bindings == null ? 0 : bindings.size()) + " 个");
        }

        // 校验每个绑定
        for (CellBindRequest bind : bindings) {
            validateBinding(bind.getDeviceId(), bind.getChannelName(), request.getCellWidth(), request.getCellHeight());
        }
        // 校验通道不超限（含当前请求中的设备绑定数 + 数据库已有绑定数）
        java.util.Map<Long, java.util.Map<String, Integer>> reqDeviceChannelCount = new java.util.HashMap<>();
        for (CellBindRequest bind : bindings) {
            reqDeviceChannelCount
                .computeIfAbsent(bind.getDeviceId(), k -> new java.util.HashMap<>())
                .merge(bind.getChannelName(), 1, Integer::sum);
        }
        for (CellBindRequest bind : bindings) {
            // 同一设备同一通道在一个请求中最多出现一次
            int reqCount = reqDeviceChannelCount.getOrDefault(bind.getDeviceId(), java.util.Collections.emptyMap())
                    .getOrDefault(bind.getChannelName(), 0);
            if (reqCount > 1) {
                DevicePageVO dev = deviceMapper.findById(bind.getDeviceId());
                throw new RuntimeException("输出通道 " + dev.getDeviceName() + ":" + bind.getChannelName()
                        + " 不能同时绑定到多个单元");
            }
            // 数据库中该设备已绑定总数 + 当前请求中该设备的新绑定数 ≤ 可用通道数
            int dbBindings = screenMapper.countDeviceBindings(bind.getDeviceId());
            int reqBindings = reqDeviceChannelCount.get(bind.getDeviceId()).size();
            if (dbBindings + reqBindings > countAvailableOutputChannels(bind.getDeviceId())) {
                DevicePageVO dev = deviceMapper.findById(bind.getDeviceId());
                throw new RuntimeException("设备 " + dev.getDeviceName() + " 输出通道已全部被占用"
                        + "（已有 " + dbBindings + " 个绑定，本次请求 " + reqBindings + " 个，上限 " + countAvailableOutputChannels(bind.getDeviceId()) + " 个）");
            }
        }

        // 插入大屏
        Screen screen = new Screen();
        screen.setScreenName(request.getScreenName());
        screen.setRowsCount(request.getRowsCount());
        screen.setColsCount(request.getColsCount());
        screen.setCellWidth(request.getCellWidth());
        screen.setCellHeight(request.getCellHeight());
        screenMapper.insertScreen(screen);

        // 构建单元列表
        List<ScreenCell> cells = new ArrayList<>();
        for (CellBindRequest bind : bindings) {
            ScreenCell cell = new ScreenCell();
            cell.setScreenId(screen.getId());
            cell.setRowIndex(bind.getRowIndex());
            cell.setColIndex(bind.getColIndex());
            cell.setDeviceId(bind.getDeviceId());
            cell.setChannelName(bind.getChannelName());
            cells.add(cell);
        }
        screenMapper.insertCells(cells);

        return buildDetail(screen);
    }

    /**
     * 分页查询大屏列表
     *
     * @param dto 分页查询条件（名称模糊搜索）
     * @return 分页结果
     */
    @Override
    public PageResult<ScreenPageVO> getPage(ScreenPageDTO dto) {
        if (dto.getPage() == null) dto.setPage(1);
        if (dto.getPageSize() == null) dto.setPageSize(10);
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        Page<ScreenPageVO> page = screenMapper.pageQuery(dto);
        return new PageResult<>(page.getTotal(), page.getResult());
    }

    /**
     * 获取大屏详情（含单元列表）
     *
     * @param id 大屏 ID
     * @return 大屏详情
     */
    @Override
    public ScreenDetailVO getDetail(Long id) {
        Screen screen = screenMapper.findById(id);
        if (screen == null) {
            throw new RuntimeException("大屏不存在: id=" + id);
        }
        return buildDetail(screen);
    }

    /**
     * 删除大屏（级联删除单元和窗口）
     *
     * @param id 大屏 ID
     */
    @Override
    @Transactional
    public void deleteScreen(Long id) {
        Screen screen = screenMapper.findById(id);
        if (screen == null) {
            throw new RuntimeException("大屏不存在: id=" + id);
        }
        // 先关闭所有窗口（通知输出/输入设备清理），再删除大屏
        windowService.clearWindows(id);
        screenMapper.deleteScreen(id);
    }

    /**
     * 绑定/更换设备通道
     * <p>
     * 校验设备有效性、通道可用性，不支持解绑（只能更换绑定）。
     *
     * @param screenId 大屏 ID
     * @param cellId   单元 ID
     * @param request  绑定参数（设备 ID、通道名）
     * @return 更新后的单元 VO
     */
    @Override
    @Transactional
    public CellVO bindCell(Long screenId, Long cellId, CellBindRequest request) {
        ScreenCell cell = screenMapper.findCellById(cellId);
        if (cell == null || !cell.getScreenId().equals(screenId)) {
            throw new RuntimeException("单元不存在");
        }
        if (request.getDeviceId() == null) {
            throw new RuntimeException("不允许解绑，只能更换绑定设备");
        }

        validateBinding(request.getDeviceId(), request.getChannelName(),
                getScreenCellWidth(screenId), getScreenCellHeight(screenId));

        // 校验通道总数不超限（排除当前单元自身）
        int currentBindings = screenMapper.countDeviceBindings(request.getDeviceId());
        int availableChannels = countAvailableOutputChannels(request.getDeviceId());
        // 如果是更换绑定（同一设备同一通道），不增加计数
        boolean isSameBinding = request.getDeviceId().equals(cell.getDeviceId())
                && request.getChannelName().equals(cell.getChannelName());
        if (!isSameBinding && currentBindings >= availableChannels) {
            DevicePageVO dev = deviceMapper.findById(request.getDeviceId());
            throw new RuntimeException("设备 " + dev.getDeviceName() + " 输出通道已全部被占用");
        }

        screenMapper.updateCellBinding(cellId, request.getDeviceId(), request.getChannelName());
        return toCellVO(cellId, screenId, request.getDeviceId(), request.getChannelName());
    }

    // ---- 私有方法 ----

    private void validateBinding(Long deviceId, String channelName, int cellWidth, int cellHeight) {
        DevicePageVO device = deviceMapper.findById(deviceId);
        if (device == null) {
            throw new RuntimeException("设备不存在: " + deviceId);
        }
        if (device.getEnabled() != null && device.getEnabled() != 1) {
            throw new RuntimeException("设备 " + device.getDeviceName() + " 已禁用，不允许绑定");
        }
        if (device.getOnline() == null || device.getOnline() != 1) {
            throw new RuntimeException("设备 " + device.getDeviceName() + " 当前离线，不允许绑定");
        }
        if (!"OUTPUT".equals(device.getDeviceCategory())) {
            throw new RuntimeException("该设备为输入设备，不可用于大屏绑定");
        }
        // 校验通道名是否为该设备的有效输出通道
        boolean validChannel = channelName.equals(device.getOutputChannel1())
                || channelName.equals(device.getOutputChannel2())
                || channelName.equals(device.getOutputChannel3());
        if (!validChannel) {
            throw new RuntimeException("通道名无效: " + channelName);
        }
        // 校验分辨率匹配
        String expectedRes = cellWidth + "x" + cellHeight;
        if (!expectedRes.equals(device.getMaxResolution())) {
            throw new RuntimeException("设备分辨率 " + device.getMaxResolution()
                    + " 不匹配大屏单元分辨率 " + expectedRes);
        }
    }

    private int countAvailableOutputChannels(Long deviceId) {
        DevicePageVO device = deviceMapper.findById(deviceId);
        int count = 0;
        if (StringUtils.hasText(device.getOutputChannel1())) count++;
        if (StringUtils.hasText(device.getOutputChannel2())) count++;
        if (StringUtils.hasText(device.getOutputChannel3())) count++;
        return count;
    }

    private int getScreenCellWidth(Long screenId) {
        Screen s = screenMapper.findById(screenId);
        return s != null ? s.getCellWidth() : 1920;
    }

    private int getScreenCellHeight(Long screenId) {
        Screen s = screenMapper.findById(screenId);
        return s != null ? s.getCellHeight() : 1080;
    }

    private ScreenDetailVO buildDetail(Screen screen) {
        ScreenDetailVO vo = new ScreenDetailVO();
        vo.setId(screen.getId());
        vo.setScreenName(screen.getScreenName());
        vo.setRowsCount(screen.getRowsCount());
        vo.setColsCount(screen.getColsCount());
        vo.setCellWidth(screen.getCellWidth());
        vo.setCellHeight(screen.getCellHeight());
        vo.setCells(screenMapper.findCellsByScreenId(screen.getId()));
        if (screen.getCreateTime() != null) {
            vo.setCreateTime(screen.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        return vo;
    }

    private CellVO toCellVO(Long cellId, Long screenId, Long deviceId, String channelName) {
        ScreenCell cell = screenMapper.findCellById(cellId);
        CellVO vo = new CellVO();
        vo.setId(cell.getId());
        vo.setScreenId(screenId);
        vo.setRowIndex(cell.getRowIndex());
        vo.setColIndex(cell.getColIndex());
        vo.setDeviceId(deviceId);
        vo.setChannelName(channelName);

        if (deviceId != null) {
            DevicePageVO dev = deviceMapper.findById(deviceId);
            if (dev != null) {
                vo.setDeviceName(dev.getDeviceName());
                vo.setDeviceType(dev.getDeviceType());
                vo.setDeviceCategory(dev.getDeviceCategory());
                vo.setOnline(dev.getOnline());
                vo.setBaseUrl(dev.getBaseUrl());
            }
        }
        return vo;
    }

}