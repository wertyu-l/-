package com.example.demo.service.impl;

import com.example.demo.common.*;
import com.example.demo.driver.DeviceEndpoint;
import com.example.demo.driver.DeviceDriver;
import com.example.demo.entity.Screen;
import com.example.demo.entity.ScreenWindow;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.mapper.ScreenMapper;
import com.example.demo.mapper.WindowMapper;
import com.example.demo.model.SimWindow;
import com.example.demo.service.WindowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 窗口管理 Service 实现
 * <p>
 * 双写机制：创建/更新先写 DB 再同步设备；删除先同步设备再删 DB。
 * <p>
 * 跨单元拆分：窗口在大屏上跨多个单元时，按单元动态拆分为子窗口分别推送给各单元绑定的输出设备。
 * <p>
 * 降级处理：窗口覆盖的某个输出设备离线时，跳过推送并标记 degraded=1，设备恢复后定时任务自动补推。
 */
@Service
public class WindowServiceImpl implements WindowService {

    @Autowired
    private WindowMapper windowMapper;

    @Autowired
    private ScreenMapper screenMapper;

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private DeviceDriver deviceDriver;

    @Override
    @Transactional
    public ScreenWindowVO createWindow(Long screenId, WindowCreateRequest request) {
        if (!StringUtils.hasText(request.getWindowId())) {
            throw new RuntimeException("窗口ID不能为空");
        }
        if (request.getDeviceId() == null) {
            throw new RuntimeException("设备ID不能为空");
        }
        if (!StringUtils.hasText(request.getChannelName())) {
            throw new RuntimeException("通道名不能为空");
        }

        Screen screen = screenMapper.findById(screenId);
        if (screen == null) {
            throw new RuntimeException("大屏不存在: id=" + screenId);
        }

        // 写入 DB（先标记 pending）
        ScreenWindow sw = new ScreenWindow();
        sw.setWindowId(request.getWindowId());
        sw.setScreenId(screenId);
        sw.setDeviceId(request.getDeviceId());
        sw.setChannelName(request.getChannelName());
        sw.setX(request.getX() != null ? request.getX() : 0);
        sw.setY(request.getY() != null ? request.getY() : 0);
        sw.setWidth(request.getWidth() != null ? request.getWidth() : 960);
        sw.setHeight(request.getHeight() != null ? request.getHeight() : 540);
        sw.setSyncStatus("pending");
        sw.setDegraded(0);
        windowMapper.insert(sw);

        // 跨单元同步到设备
        syncToCoveredDevices(sw, screen, false);

        DevicePageVO device = deviceMapper.findById(request.getDeviceId());
        return toVO(windowMapper.findByWindowId(request.getWindowId()), device);
    }

    @Override
    @Transactional
    public ScreenWindowVO updateWindow(Long screenId, String windowId, WindowUpdateRequest request) {
        ScreenWindow sw = windowMapper.findByWindowId(windowId);
        if (sw == null || !sw.getScreenId().equals(screenId)) {
            throw new RuntimeException("窗口不存在: " + windowId);
        }

        Screen screen = screenMapper.findById(screenId);

        // 更新 DB 位置/大小
        Integer newX = request.getX() != null ? request.getX() : sw.getX();
        Integer newY = request.getY() != null ? request.getY() : sw.getY();
        Integer newW = request.getWidth() != null ? request.getWidth() : sw.getWidth();
        Integer newH = request.getHeight() != null ? request.getHeight() : sw.getHeight();
        windowMapper.updatePosition(windowId, newX, newY, newW, newH);

        // 刷新 sw 对象
        sw.setX(newX);
        sw.setY(newY);
        sw.setWidth(newW);
        sw.setHeight(newH);

        // 跨单元同步到设备
        syncToCoveredDevices(sw, screen, true);

        DevicePageVO device = deviceMapper.findById(sw.getDeviceId());
        return toVO(windowMapper.findByWindowId(windowId), device);
    }

    @Override
    @Transactional
    public void closeWindow(Long screenId, String windowId) {
        ScreenWindow sw = windowMapper.findByWindowId(windowId);
        if (sw == null || !sw.getScreenId().equals(screenId)) {
            throw new RuntimeException("窗口不存在: " + windowId);
        }

        Screen screen = screenMapper.findById(screenId);
        List<CellCoverage> coverages = calcCoverages(sw, screen);

        boolean allClosed = true;
        for (CellCoverage cov : coverages) {
            DevicePageVO dev = deviceMapper.findById(cov.deviceId);
            if (dev == null || dev.getOnline() == null || dev.getOnline() != 1) {
                continue; // 设备离线，关闭不需要同步
            }
            try {
                DeviceEndpoint endpoint = buildEndpoint(dev);
                deviceDriver.closeWindow(endpoint, windowId);
            } catch (Exception e) {
                allClosed = false;
            }
        }

        if (!allClosed && !coverages.isEmpty()) {
            // 降级窗口（设备离线），允许关闭并清除标记
        }

        windowMapper.deleteByWindowId(windowId);
    }

    @Override
    public List<ScreenWindowVO> getWindows(Long screenId) {
        return windowMapper.findByScreenId(screenId);
    }

    @Override
    @Transactional
    public void clearWindows(Long screenId) {
        List<ScreenWindowVO> windows = windowMapper.findByScreenId(screenId);
        for (ScreenWindowVO w : windows) {
            try {
                closeWindow(screenId, w.getWindowId());
            } catch (Exception ignored) {
            }
        }
    }

    // ==================== 跨单元拆分与降级 ====================

    /**
     * 计算窗口覆盖的单元列表
     */
    private List<CellCoverage> calcCoverages(ScreenWindow sw, Screen screen) {
        if (screen == null) return List.of();
        List<CellVO> cells = screenMapper.findCellsByScreenId(screen.getId());

        int cellW = screen.getCellWidth();
        int cellH = screen.getCellHeight();
        int startRow = Math.max(0, sw.getY() / cellH);
        int endRow = (sw.getY() + sw.getHeight() - 1) / cellH;
        int startCol = Math.max(0, sw.getX() / cellW);
        int endCol = (sw.getX() + sw.getWidth() - 1) / cellW;

        // 限制在大屏范围内
        endRow = Math.min(endRow, screen.getRowsCount() - 1);
        endCol = Math.min(endCol, screen.getColsCount() - 1);

        List<CellCoverage> result = new ArrayList<>();
        for (int r = startRow; r <= endRow; r++) {
            for (int c = startCol; c <= endCol; c++) {
                CellVO cell = findCell(cells, r, c);
                if (cell == null || cell.getDeviceId() == null) continue;

                // 计算子窗口坐标
                int subX = Math.max(0, sw.getX() - c * cellW);
                int subY = Math.max(0, sw.getY() - r * cellH);
                int subW = Math.min(cellW - subX, sw.getX() + sw.getWidth() - c * cellW - subX);
                int subH = Math.min(cellH - subY, sw.getY() + sw.getHeight() - r * cellH - subY);

                result.add(new CellCoverage(cell.getDeviceId(), cell.getChannelName(), subX, subY, subW, subH));
            }
        }
        return result;
    }

    private CellVO findCell(List<CellVO> cells, int row, int col) {
        return cells.stream()
                .filter(c -> c.getRowIndex() == row && c.getColIndex() == col)
                .findFirst().orElse(null);
    }

    /**
     * 将窗口按覆盖单元拆分并同步到各设备
     *
     * @param sw        窗口
     * @param screen    大屏
     * @param isUpdate  是否更新（true=先关闭旧子窗口再创建新的）
     */
    private void syncToCoveredDevices(ScreenWindow sw, Screen screen, boolean isUpdate) {
        List<CellCoverage> coverages = calcCoverages(sw, screen);
        if (coverages.isEmpty()) return;

        boolean anyOffline = false;
        int successCount = 0;

        for (CellCoverage cov : coverages) {
            DevicePageVO dev = deviceMapper.findById(cov.deviceId);
            if (dev == null) continue;

            boolean deviceOnline = dev.getOnline() != null && dev.getOnline() == 1;
            if (!deviceOnline) {
                anyOffline = true;
                continue;
            }

            try {
                DeviceEndpoint endpoint = buildEndpoint(dev);

                // 更新时先关闭旧子窗口
                if (isUpdate) {
                    try { deviceDriver.closeWindow(endpoint, sw.getWindowId()); } catch (Exception ignored) {}
                }

                SimWindow simWindow = new SimWindow();
                simWindow.setWindowId(sw.getWindowId());
                simWindow.setChannelName(cov.channelName);
                simWindow.setX(cov.x);
                simWindow.setY(cov.y);
                simWindow.setWidth(cov.w);
                simWindow.setHeight(cov.h);

                Result<SimWindow> result = deviceDriver.createWindow(endpoint, simWindow);
                if (result != null && result.getCode() == 1) {
                    successCount++;
                }
            } catch (Exception ignored) {
                anyOffline = true;
            }
        }

        // 更新同步状态和降级标记
        if (successCount == coverages.size()) {
            windowMapper.updateDegraded(sw.getWindowId(), "synced", 0);
        } else if (anyOffline || successCount < coverages.size()) {
            windowMapper.updateDegraded(sw.getWindowId(), "pending", 1);
        } else {
            windowMapper.updateSyncStatus(sw.getWindowId(), "synced");
        }
    }

    // ==================== 定时重试 ====================

    /**
     * 定时重试 pending/failed 窗口（每 30 秒）
     * <p>
     * 设备恢复上线后，重新推送子窗口并清除降级标记。
     */
    @Scheduled(fixedDelay = 30_000)
    public void retryPendingWindows() {
        List<ScreenWindow> pending = windowMapper.findBySyncStatus(List.of("pending", "failed"));
        for (ScreenWindow w : pending) {
            Screen screen = screenMapper.findById(w.getScreenId());
            if (screen == null) continue;

            syncToCoveredDevices(w, screen, true);
        }
    }

    // ==================== 辅助方法 ====================

    private ScreenWindowVO toVO(ScreenWindow sw, DevicePageVO device) {
        ScreenWindowVO vo = new ScreenWindowVO();
        vo.setWindowId(sw.getWindowId());
        vo.setScreenId(sw.getScreenId());
        vo.setDeviceId(sw.getDeviceId());
        vo.setChannelName(sw.getChannelName());
        vo.setX(sw.getX());
        vo.setY(sw.getY());
        vo.setWidth(sw.getWidth());
        vo.setHeight(sw.getHeight());
        vo.setSourceType(sw.getSourceType());
        vo.setSourceUrl(sw.getSourceUrl());
        vo.setSyncStatus(sw.getSyncStatus());
        vo.setDegraded(sw.getDegraded());
        if (sw.getCreateTime() != null) vo.setCreateTime(sw.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        if (sw.getUpdateTime() != null) vo.setUpdateTime(sw.getUpdateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        if (device != null) {
            vo.setDeviceName(device.getDeviceName());
            vo.setDeviceCategory(device.getDeviceCategory());
        }
        return vo;
    }

    private DeviceEndpoint buildEndpoint(DevicePageVO device) {
        DeviceEndpoint endpoint = new DeviceEndpoint();
        endpoint.setDeviceType(device.getDeviceType());
        endpoint.setBaseUrl(device.getBaseUrl());
        return endpoint;
    }

    /**
     * 窗口覆盖的单个单元信息（子窗口坐标）
     */
    private static class CellCoverage {
        final Long deviceId;
        final String channelName;
        final int x, y, w, h;

        CellCoverage(Long deviceId, String channelName, int x, int y, int w, int h) {
            this.deviceId = deviceId;
            this.channelName = channelName;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

}