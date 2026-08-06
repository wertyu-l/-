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
 */
@Service
public class ScreenServiceImpl implements ScreenService {

    @Autowired
    private ScreenMapper screenMapper;

    @Autowired
    private DeviceMapper deviceMapper;

    @Override
    @Transactional
    public ScreenDetailVO createScreen(ScreenCreateRequest request) {
        if (!StringUtils.hasText(request.getScreenName())) {
            throw new RuntimeException("大屏名称不能为空");
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
        // 校验通道不超限
        for (CellBindRequest bind : bindings) {
            int currentBindings = screenMapper.countDeviceBindings(bind.getDeviceId());
            if (currentBindings >= countAvailableOutputChannels(bind.getDeviceId())) {
                DevicePageVO dev = deviceMapper.findById(bind.getDeviceId());
                throw new RuntimeException("设备 " + dev.getDeviceName() + " 输出通道已全部被占用");
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

    @Override
    public PageResult<ScreenPageVO> getPage(ScreenPageDTO dto) {
        if (dto.getPage() == null) dto.setPage(1);
        if (dto.getPageSize() == null) dto.setPageSize(10);
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        Page<ScreenPageVO> page = screenMapper.pageQuery(dto);
        return new PageResult<>(page.getTotal(), page.getResult());
    }

    @Override
    public ScreenDetailVO getDetail(Long id) {
        Screen screen = screenMapper.findById(id);
        if (screen == null) {
            throw new RuntimeException("大屏不存在: id=" + id);
        }
        return buildDetail(screen);
    }

    @Override
    @Transactional
    public void deleteScreen(Long id) {
        Screen screen = screenMapper.findById(id);
        if (screen == null) {
            throw new RuntimeException("大屏不存在: id=" + id);
        }
        screenMapper.deleteScreen(id);
    }

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