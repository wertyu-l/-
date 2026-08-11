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

        // 校验窗口数限制：按每个设备单元的 maxWindows 独立限制
        List<CellVO> cells = screenMapper.findCellsByScreenId(screenId);
        List<ScreenWindowVO> existingWindows = windowMapper.findByScreenId(screenId);

        int cellW = screen.getCellWidth();
        int cellH = screen.getCellHeight();

        int wx = request.getX() != null ? request.getX() : 0;
        int wy = request.getY() != null ? request.getY() : 0;
        int ww = request.getWidth() != null ? request.getWidth() : 960;
        int wh = request.getHeight() != null ? request.getHeight() : 540;

        for (CellVO cell : cells) {
            if (cell.getDeviceId() == null || cell.getMaxWindows() == null) continue;

            if (!windowCoversCell(wx, wy, ww, wh,
                    cell.getRowIndex(), cell.getColIndex(), cellW, cellH)) {
                continue;
            }

            int countOnCell = 0;
            for (ScreenWindowVO existing : existingWindows) {
                int ex = existing.getX() != null ? existing.getX() : 0;
                int ey = existing.getY() != null ? existing.getY() : 0;
                int ew = existing.getWidth() != null ? existing.getWidth() : 960;
                int eh = existing.getHeight() != null ? existing.getHeight() : 540;
                if (windowCoversCell(ex, ey, ew, eh,
                        cell.getRowIndex(), cell.getColIndex(), cellW, cellH)) {
                    countOnCell++;
                }
            }

            if (countOnCell >= cell.getMaxWindows()) {
                throw new RuntimeException("设备 [" + cell.getDeviceName()
                        + "] 窗口数已达上限（" + cell.getMaxWindows() + "）");
            }
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
        sw.setSourceUrl(request.getSourceUrl());
        sw.setSyncStatus("pending");
        sw.setDegraded(0);

        // 校验输出设备能力限制（窗口叠加）
        validateCreateCapabilities(sw, screen);

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

        // 校验设备能力限制（移动、缩放、叠加），先构造临时对象
        boolean moveRequested = request.getX() != null || request.getY() != null;
        boolean resizeRequested = request.getWidth() != null || request.getHeight() != null;
        if (moveRequested || resizeRequested) {
            // 校验窗口数限制：移动/缩放后，新位置覆盖的每个单元不能超过 maxWindows
            List<CellVO> cells = screenMapper.findCellsByScreenId(screenId);
            List<ScreenWindowVO> existingWindows = windowMapper.findByScreenId(screenId);
            int cellW = screen.getCellWidth();
            int cellH = screen.getCellHeight();

            for (CellVO cell : cells) {
                if (cell.getDeviceId() == null || cell.getMaxWindows() == null) continue;

                if (!windowCoversCell(newX, newY, newW, newH,
                        cell.getRowIndex(), cell.getColIndex(), cellW, cellH)) {
                    continue;
                }

                int countOnCell = 0;
                for (ScreenWindowVO existing : existingWindows) {
                    if (existing.getWindowId().equals(windowId)) continue;

                    int ex = existing.getX() != null ? existing.getX() : 0;
                    int ey = existing.getY() != null ? existing.getY() : 0;
                    int ew = existing.getWidth() != null ? existing.getWidth() : 960;
                    int eh = existing.getHeight() != null ? existing.getHeight() : 540;
                    if (windowCoversCell(ex, ey, ew, eh,
                            cell.getRowIndex(), cell.getColIndex(), cellW, cellH)) {
                        countOnCell++;
                    }
                }

                if (countOnCell >= cell.getMaxWindows()) {
                    throw new RuntimeException("设备 [" + cell.getDeviceName()
                            + "] 窗口数已达上限（" + cell.getMaxWindows() + "）");
                }
            }

            ScreenWindow tempSw = new ScreenWindow();
            tempSw.setWindowId(windowId);
            tempSw.setDeviceId(sw.getDeviceId());
            tempSw.setX(newX);
            tempSw.setY(newY);
            tempSw.setWidth(newW);
            tempSw.setHeight(newH);
            validateUpdateCapabilities(tempSw, screen, moveRequested, resizeRequested);
        }

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

        // 先标记为 closing，防止 retryPendingWindows 定时任务在清理期间重新创建窗口
        windowMapper.updateSyncStatus(windowId, "closing");

        boolean allClosed = true;

        // 关闭窗口在所有已绑定输出设备上的残留（遍历所有单元，按 deviceId 去重，跳过离线设备）
        java.util.Set<Long> closedDeviceIds = new java.util.HashSet<>();
        List<CellVO> allCells = screenMapper.findCellsByScreenId(screenId);
        for (CellVO cell : allCells) {
            if (cell.getDeviceId() == null) continue;
            if (closedDeviceIds.contains(cell.getDeviceId())) continue;
            closedDeviceIds.add(cell.getDeviceId());
            DevicePageVO dev = deviceMapper.findById(cell.getDeviceId());
            if (dev == null) continue;
            if (dev.getOnline() == null || dev.getOnline() != 1) continue;
            try {
                deviceDriver.closeWindow(buildEndpoint(dev), windowId);
            } catch (Exception e) {
                allClosed = false;
            }
        }

        // 关闭输入设备（信号源）上的窗口
        DevicePageVO sourceDev = deviceMapper.findById(sw.getDeviceId());
        if (sourceDev != null && sourceDev.getOnline() != null && sourceDev.getOnline() == 1) {
            try {
                deviceDriver.closeWindow(buildEndpoint(sourceDev), windowId);
            } catch (Exception e) {
                allClosed = false;
            }
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

    @Override
    public List<OutputDeviceWindowsVO> getOutputDeviceWindows(Long screenId) {
        Screen screen = screenMapper.findById(screenId);
        if (screen == null) {
            throw new RuntimeException("大屏不存在: id=" + screenId);
        }

        List<CellVO> cells = screenMapper.findCellsByScreenId(screenId);
        List<ScreenWindowVO> windows = windowMapper.findByScreenId(screenId);

        int cellW = screen.getCellWidth();
        int cellH = screen.getCellHeight();

        List<OutputDeviceWindowsVO> result = new ArrayList<>();

        for (CellVO cell : cells) {
            if (cell.getDeviceId() == null) continue; // 跳过未绑定设备的单元

            OutputDeviceWindowsVO vo = new OutputDeviceWindowsVO();
            vo.setCellId(cell.getId());
            vo.setRowIndex(cell.getRowIndex());
            vo.setColIndex(cell.getColIndex());
            vo.setDeviceId(cell.getDeviceId());
            vo.setDeviceName(cell.getDeviceName());
            vo.setChannelName(cell.getChannelName());
            vo.setOnline(cell.getOnline());
            vo.setBaseUrl(cell.getBaseUrl());
            vo.setMaxWindows(cell.getMaxWindows());
            vo.setSupportMove(cell.getSupportMove());
            vo.setSupportResize(cell.getSupportResize());
            vo.setSupportOverlay(cell.getSupportOverlay());
            vo.setMaxResolution(cell.getMaxResolution());

            List<SubWindowVO> subWindows = new ArrayList<>();

            for (ScreenWindowVO win : windows) {
                // 计算窗口是否覆盖此单元
                int winLeft = win.getX() != null ? win.getX() : 0;
                int winTop = win.getY() != null ? win.getY() : 0;
                int winRight = winLeft + (win.getWidth() != null ? win.getWidth() : 960);
                int winBottom = winTop + (win.getHeight() != null ? win.getHeight() : 540);

                int cellLeft = cell.getColIndex() * cellW;
                int cellTop = cell.getRowIndex() * cellH;
                int cellRight = cellLeft + cellW;
                int cellBottom = cellTop + cellH;

                // 矩形相交检测
                if (winLeft < cellRight && winRight > cellLeft
                        && winTop < cellBottom && winBottom > cellTop) {

                    // 计算子窗口在此单元内的坐标
                    int subX = Math.max(0, winLeft - cellLeft);
                    int subY = Math.max(0, winTop - cellTop);
                    int subW = Math.min(cellW - subX, winRight - cellLeft - subX);
                    int subH = Math.min(cellH - subY, winBottom - cellTop - subY);

                    SubWindowVO sub = new SubWindowVO();
                    sub.setWindowId(win.getWindowId());
                    sub.setSourceDeviceName(win.getDeviceName());
                    sub.setSourceChannelName(win.getChannelName());
                    sub.setX(subX);
                    sub.setY(subY);
                    sub.setWidth(subW);
                    sub.setHeight(subH);
                    sub.setSourceType(win.getSourceType());
                    sub.setSourceUrl(win.getSourceUrl());
                    sub.setSyncStatus(win.getSyncStatus());
                    sub.setDegraded(win.getDegraded());

                    subWindows.add(sub);
                }
            }

            vo.setWindows(subWindows);
            result.add(vo);
        }

        return result;
    }

    // ==================== 设备能力校验 ====================

    /**
     * 校验创建窗口时的设备能力限制（窗口叠加）
     * <p>
     * 遍历窗口覆盖的所有单元，对不支持叠加（supportOverlay=0）的单元，
     * 检查是否已有其他窗口也覆盖该单元，若存在重叠则拒绝创建。
     */
    private void validateCreateCapabilities(ScreenWindow sw, Screen screen) {
        List<CellVO> cells = screenMapper.findCellsByScreenId(screen.getId());
        List<ScreenWindowVO> existingWindows = windowMapper.findByScreenId(screen.getId());

        int cellW = screen.getCellWidth();
        int cellH = screen.getCellHeight();

        int wx = sw.getX() != null ? sw.getX() : 0;
        int wy = sw.getY() != null ? sw.getY() : 0;
        int ww = sw.getWidth() != null ? sw.getWidth() : 960;
        int wh = sw.getHeight() != null ? sw.getHeight() : 540;

        for (CellVO cell : cells) {
            if (cell.getDeviceId() == null) continue;
            if (cell.getSupportOverlay() != null && cell.getSupportOverlay() == 1) continue;

            // 该单元不支持叠加，检查新窗口是否覆盖此单元
            if (!windowCoversCell(wx, wy, ww, wh, cell.getRowIndex(), cell.getColIndex(), cellW, cellH)) {
                continue;
            }

            // 检查是否有其他窗口也覆盖此单元
            for (ScreenWindowVO existing : existingWindows) {
                int ex = existing.getX() != null ? existing.getX() : 0;
                int ey = existing.getY() != null ? existing.getY() : 0;
                int ew = existing.getWidth() != null ? existing.getWidth() : 960;
                int eh = existing.getHeight() != null ? existing.getHeight() : 540;

                if (windowCoversCell(ex, ey, ew, eh, cell.getRowIndex(), cell.getColIndex(), cellW, cellH)) {
                    throw new RuntimeException("设备 [" + cell.getDeviceName()
                            + "] 不支持窗口叠加，新窗口 [" + sw.getWindowId()
                            + "] 与已有窗口 [" + existing.getWindowId()
                            + "] 在单元 [" + cell.getRowIndex() + "," + cell.getColIndex() + "] 上重叠");
                }
            }
        }
    }

    /**
     * 校验更新窗口时的设备能力限制（移动、缩放、叠加）
     * <p>
     * 遍历窗口覆盖的所有单元，根据请求中变更的维度逐一检查对应能力：
     * <ul>
     *   <li>移动（x/y 变化）→ 检查 supportMove</li>
     *   <li>缩放（width/height 变化）→ 检查 supportResize</li>
     *   <li>矩形变化 → 重新检查叠加，避免新的重叠出现在不支持叠加的设备上</li>
     * </ul>
     */
    private void validateUpdateCapabilities(ScreenWindow sw, Screen screen,
                                            boolean moveRequested, boolean resizeRequested) {
        List<CellVO> cells = screenMapper.findCellsByScreenId(screen.getId());
        List<ScreenWindowVO> existingWindows = windowMapper.findByScreenId(screen.getId());

        int cellW = screen.getCellWidth();
        int cellH = screen.getCellHeight();

        int wx = sw.getX() != null ? sw.getX() : 0;
        int wy = sw.getY() != null ? sw.getY() : 0;
        int ww = sw.getWidth() != null ? sw.getWidth() : 960;
        int wh = sw.getHeight() != null ? sw.getHeight() : 540;

        for (CellVO cell : cells) {
            if (cell.getDeviceId() == null) continue;
            if (!windowCoversCell(wx, wy, ww, wh, cell.getRowIndex(), cell.getColIndex(), cellW, cellH)) {
                continue;
            }

            // 检查移动能力（纯移动才检查，缩放附带的位置变化不在此列）
            if (moveRequested && !resizeRequested
                    && cell.getSupportMove() != null && cell.getSupportMove() == 0) {
                throw new RuntimeException("设备 [" + cell.getDeviceName() + "] 不支持窗口移动");
            }

            // 检查缩放能力
            if (resizeRequested && cell.getSupportResize() != null && cell.getSupportResize() == 0) {
                throw new RuntimeException("设备 [" + cell.getDeviceName() + "] 不支持窗口缩放");
            }
        }

        // 检查叠加能力（矩形变化后可能产生新的重叠）
        for (CellVO cell : cells) {
            if (cell.getDeviceId() == null) continue;
            if (cell.getSupportOverlay() != null && cell.getSupportOverlay() == 1) continue;
            if (!windowCoversCell(wx, wy, ww, wh, cell.getRowIndex(), cell.getColIndex(), cellW, cellH)) {
                continue;
            }

            for (ScreenWindowVO existing : existingWindows) {
                if (existing.getWindowId().equals(sw.getWindowId())) continue;

                int ex = existing.getX() != null ? existing.getX() : 0;
                int ey = existing.getY() != null ? existing.getY() : 0;
                int ew = existing.getWidth() != null ? existing.getWidth() : 960;
                int eh = existing.getHeight() != null ? existing.getHeight() : 540;

                if (windowCoversCell(ex, ey, ew, eh, cell.getRowIndex(), cell.getColIndex(), cellW, cellH)) {
                    throw new RuntimeException("设备 [" + cell.getDeviceName()
                            + "] 不支持窗口叠加，窗口 [" + sw.getWindowId()
                            + "] 与窗口 [" + existing.getWindowId()
                            + "] 在单元 [" + cell.getRowIndex() + "," + cell.getColIndex() + "] 上重叠");
                }
            }
        }
    }

    /**
     * 判断窗口矩形是否覆盖指定单元（矩形相交检测）
     */
    private boolean windowCoversCell(int wx, int wy, int ww, int wh,
                                     int cellRow, int cellCol, int cellW, int cellH) {
        int winLeft = wx;
        int winTop = wy;
        int winRight = wx + ww;
        int winBottom = wy + wh;

        int cellLeft = cellCol * cellW;
        int cellTop = cellRow * cellH;
        int cellRight = cellLeft + cellW;
        int cellBottom = cellTop + cellH;

        return winLeft < cellRight && winRight > cellLeft
                && winTop < cellBottom && winBottom > cellTop;
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

        // Phase 1: 更新时先关闭所有在线设备上的旧子窗口（离线设备跳过，避免无意义超时阻塞）
        if (isUpdate) {
            java.util.Set<Long> closedDevices = new java.util.HashSet<>();
            List<CellVO> allCells = screenMapper.findCellsByScreenId(screen.getId());
            for (CellVO cell : allCells) {
                if (cell.getDeviceId() == null) continue;
                if (closedDevices.contains(cell.getDeviceId())) continue;
                closedDevices.add(cell.getDeviceId());
                DevicePageVO dev = deviceMapper.findById(cell.getDeviceId());
                if (dev != null && dev.getOnline() != null && dev.getOnline() == 1) {
                    try { deviceDriver.closeWindow(buildEndpoint(dev), sw.getWindowId()); } catch (Exception ignored) {}
                }
            }
            // 同时关闭信号源设备上的旧窗口（仅在线时）
            DevicePageVO sourceDev = deviceMapper.findById(sw.getDeviceId());
            if (sourceDev != null && sourceDev.getOnline() != null && sourceDev.getOnline() == 1) {
                try { deviceDriver.closeWindow(buildEndpoint(sourceDev), sw.getWindowId()); } catch (Exception ignored) {}
            }
        }

        // Phase 2: 为每个覆盖单元创建子窗口
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

                SimWindow simWindow = new SimWindow();
                simWindow.setWindowId(sw.getWindowId());
                simWindow.setChannelName(cov.channelName);
                simWindow.setX(cov.x);
                simWindow.setY(cov.y);
                simWindow.setWidth(cov.w);
                simWindow.setHeight(cov.h);
                simWindow.setSourceUrl(sw.getSourceUrl());

                Result<SimWindow> result = deviceDriver.createWindow(endpoint, simWindow);
                if (result != null && result.getCode() == 1) {
                    successCount++;
                }
            } catch (Exception ignored) {
                anyOffline = true;
            }
        }

        // Phase 3: 推送完整窗口到输入设备（信号源）
        int totalTargets = coverages.size() + 1; // 输出设备 + 输入设备
        DevicePageVO sourceDev = deviceMapper.findById(sw.getDeviceId());
        if (sourceDev != null) {
            boolean sourceOnline = sourceDev.getOnline() != null && sourceDev.getOnline() == 1;
            if (sourceOnline) {
                try {
                    DeviceEndpoint endpoint = buildEndpoint(sourceDev);
                    SimWindow simWindow = new SimWindow();
                    simWindow.setWindowId(sw.getWindowId());
                    simWindow.setChannelName(sw.getChannelName());
                    simWindow.setX(sw.getX());
                    simWindow.setY(sw.getY());
                    simWindow.setWidth(sw.getWidth());
                    simWindow.setHeight(sw.getHeight());
                    simWindow.setSourceUrl(sw.getSourceUrl());
                    Result<SimWindow> result = deviceDriver.createWindow(endpoint, simWindow);
                    if (result != null && result.getCode() == 1) {
                        successCount++;
                    }
                } catch (Exception ignored) {
                    anyOffline = true;
                }
            } else {
                anyOffline = true;
            }
        }

        // 更新同步状态和降级标记
        if (successCount == totalTargets) {
            windowMapper.updateDegraded(sw.getWindowId(), "synced", 0);
        } else if (anyOffline || successCount < totalTargets) {
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
            // 双重检查：窗口可能已被 closeWindow 删除或标记为 closing
            ScreenWindow current = windowMapper.findByWindowId(w.getWindowId());
            if (current == null) continue;
            if (!("pending".equals(current.getSyncStatus()) || "failed".equals(current.getSyncStatus()))) continue;

            Screen screen = screenMapper.findById(w.getScreenId());
            if (screen == null) continue;

            syncToCoveredDevices(current, screen, true);
        }
    }

    // ==================== 设备恢复后窗口重同步 ====================

    /**
     * 设备恢复上线后，将其相关窗口标记为 pending，由定时重试自动补推
     */
    @Override
    public void markPendingForDevice(DevicePageVO device) {
        if ("INPUT".equals(device.getDeviceCategory())) {
            windowMapper.updateSyncStatusByDeviceId(device.getId(), "pending");
        } else {
            List<Long> screenIds = screenMapper.findScreenIdsByDeviceId(device.getId());
            if (screenIds != null) {
                for (Long screenId : screenIds) {
                    windowMapper.updateSyncStatusByScreenId(screenId, "pending");
                }
            }
        }
    }

    /**
     * 设备离线后，将其相关窗口标记为 failed + 降级
     */
    @Override
    public void markFailedForDevice(DevicePageVO device) {
        if ("INPUT".equals(device.getDeviceCategory())) {
            windowMapper.markFailedByDeviceId(device.getId(), "failed", 1);
        } else {
            List<Long> screenIds = screenMapper.findScreenIdsByDeviceId(device.getId());
            if (screenIds != null) {
                for (Long screenId : screenIds) {
                    windowMapper.markFailedByScreenId(screenId, "failed", 1);
                }
            }
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
            vo.setSupportMove(device.getSupportMove());
            vo.setSupportResize(device.getSupportResize());
            vo.setSupportOverlay(device.getSupportOverlay());
            vo.setMaxResolution(device.getMaxResolution());
            vo.setMaxWindows(device.getMaxWindows());
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