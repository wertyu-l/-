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

    /**
     * 创建窗口
     * <p>
     * 校验窗口参数、设备能力（maxWindows、叠加）、写入 DB、
     * 然后按单元拆分子窗口推送给各输出设备，完整窗口推送给输入设备。
     *
     * @param screenId 大屏 ID
     * @param request  创建参数（窗口 ID、设备 ID、通道名、坐标、尺寸）
     * @return 创建后的窗口 VO
     */
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

        // 校验窗口数限制：按输出通道的窗口数限制（每个输出通道最多 maxWindows 个窗口）
        List<CellVO> cells = screenMapper.findCellsByScreenId(screenId);
        List<ScreenWindowVO> existingWindows = windowMapper.findByScreenId(screenId);

        int cellW = screen.getCellWidth();
        int cellH = screen.getCellHeight();

        int wx = request.getX() != null ? request.getX() : 0;
        int wy = request.getY() != null ? request.getY() : 0;
        int ww = request.getWidth() != null ? request.getWidth() : 960;
        int wh = request.getHeight() != null ? request.getHeight() : 540;

        validateMaxWindows(cells, existingWindows, null, wx, wy, ww, wh, cellW, cellH);

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
        syncToCoveredDevices(sw, screen, null);

        DevicePageVO device = deviceMapper.findById(request.getDeviceId());
        return toVO(windowMapper.findByWindowId(request.getWindowId()), device);
    }

    /**
     * 更新窗口位置/大小
     * <p>
     * 校验设备能力（移动→supportMove、缩放→supportResize）、
     * 窗口数限制（maxWindows per cell），更新 DB 后
     * Phase 1 关闭旧单元的旧子窗口，Phase 2 在新单元创建子窗口，
     * Phase 3 推送完整窗口到输入设备。
     *
     * @param screenId 大屏 ID
     * @param windowId 窗口唯一标识
     * @param request  更新参数（x、y、width、height）
     * @return 更新后的窗口 VO
     */
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

        // 校验设备能力限制（移动、缩放、叠加）
        boolean moveRequested = request.getX() != null || request.getY() != null;
        boolean resizeRequested = request.getWidth() != null || request.getHeight() != null;
        if (moveRequested || resizeRequested) {
            ScreenWindow tempSw = new ScreenWindow();
            tempSw.setWindowId(windowId);
            tempSw.setDeviceId(sw.getDeviceId());
            tempSw.setX(newX);
            tempSw.setY(newY);
            tempSw.setWidth(newW);
            tempSw.setHeight(newH);

            // 移动/缩放跨到不支持对应能力的输出通道时，直接回退到原位置/尺寸（不报错）
            if (!isMoveResizeSupported(tempSw, screen, moveRequested, resizeRequested)) {
                DevicePageVO device = deviceMapper.findById(sw.getDeviceId());
                return toVO(windowMapper.findByWindowId(windowId), device);
            }

            // 校验窗口数限制：移动/缩放后，按输出通道的窗口数限制
            List<CellVO> cells = screenMapper.findCellsByScreenId(screenId);
            List<ScreenWindowVO> existingWindows = windowMapper.findByScreenId(screenId);
            int cellW = screen.getCellWidth();
            int cellH = screen.getCellHeight();

            validateMaxWindows(cells, existingWindows, windowId, newX, newY, newW, newH, cellW, cellH);

            validateUpdateCapabilities(tempSw, screen);
        }

        // 保存旧位置覆盖的单元列表，更新后再精准清理旧子窗口
        List<CellCoverage> oldCoverages = calcCoverages(sw, screen);

        windowMapper.updatePosition(windowId, newX, newY, newW, newH);

        // 刷新 sw 对象
        sw.setX(newX);
        sw.setY(newY);
        sw.setWidth(newW);
        sw.setHeight(newH);

        // 跨单元同步到设备（传入旧覆盖单元用于 Phase 1 精准关闭）
        syncToCoveredDevices(sw, screen, oldCoverages);

        DevicePageVO device = deviceMapper.findById(sw.getDeviceId());
        return toVO(windowMapper.findByWindowId(windowId), device);
    }

    /**
     * 关闭窗口
     * <p>
     * 先标记 closing 防止定时任务干扰，然后关闭所有输出设备上的子窗口，
     * 再关闭输入设备上的信号源窗口，最后删除 DB 记录。
     *
     * @param screenId 大屏 ID
     * @param windowId 窗口唯一标识
     */
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
            if (dev.getEnabled() != null && dev.getEnabled() != 1) continue;
            try {
                deviceDriver.closeWindow(buildEndpoint(dev), windowId);
            } catch (Exception e) {
                allClosed = false;
            }
        }

        // 推送输入设备（信号源）的剩余窗口快照（不含被关闭窗口，缺失即视为关闭）
        DevicePageVO sourceDev = deviceMapper.findById(sw.getDeviceId());
        if (sourceDev != null && sourceDev.getOnline() != null && sourceDev.getOnline() == 1
                && (sourceDev.getEnabled() == null || sourceDev.getEnabled() == 1)) {
            try {
                deviceDriver.notifyWindow(buildEndpoint(sourceDev), buildWindowSnapshot(sw.getDeviceId(), windowId));
            } catch (Exception e) {
                allClosed = false;
            }
        }

        windowMapper.deleteByWindowId(windowId);
    }

    /**
     * 查询大屏下所有窗口
     *
     * @param screenId 大屏 ID
     * @return 窗口列表
     */
    @Override
    public List<ScreenWindowVO> getWindows(Long screenId) {
        return windowMapper.findByScreenId(screenId);
    }

    /**
     * 清空大屏所有窗口
     * <p>
     * 遍历大屏下所有窗口逐一关闭，单个关闭失败忽略继续。
     *
     * @param screenId 大屏 ID
     */
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

    /**
     * 查询大屏按输出设备分组的子窗口视图
     * <p>
     * 遍历大屏所有单元，计算每个窗口在该单元上的子窗口坐标，
     * 返回按单元（输出设备）分组的子窗口列表，供前端展示。
     *
     * @param screenId 大屏 ID
     * @return 按输出设备分组的子窗口列表
     */
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
     * 判断窗口移动/缩放是否被其新位置覆盖的输出通道支持。
     * <p>
     * 遍历新位置覆盖的所有单元：纯移动（moveRequested 且非缩放）需对应通道 supportMove=1；
     * 缩放需 supportResize=1。任一目标通道不支持则返回 false，由调用方回退到原位置/尺寸。
     */
    private boolean isMoveResizeSupported(ScreenWindow sw, Screen screen,
                                          boolean moveRequested, boolean resizeRequested) {
        List<CellVO> cells = screenMapper.findCellsByScreenId(screen.getId());

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

            // 纯移动才检查 supportMove（缩放附带的位置变化不在此列）
            if (moveRequested && !resizeRequested
                    && cell.getSupportMove() != null && cell.getSupportMove() == 0) {
                return false;
            }
            // 缩放检查 supportResize
            if (resizeRequested && cell.getSupportResize() != null && cell.getSupportResize() == 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 校验更新窗口时的叠加能力限制。
     * <p>
     * 移动/缩放能力已由 {@link #isMoveResizeSupported} 提前校验（不支持时回退），
     * 此处仅校验矩形变化后是否在不支持叠加（supportOverlay=0）的设备上产生新的重叠。
     */
    private void validateUpdateCapabilities(ScreenWindow sw, Screen screen) {
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

    /**
     * 校验窗口数限制：按输出通道的子窗口数限制（每个输出通道最多 maxWindows 个子窗口）。
     * <p>
     * maxWindows 是「单通道」上限，而非整个设备的窗口总数；一个输出通道可创建的子窗口数
     * 不超过 maxWindows。因此按 (deviceId, channelName) 逐个通道校验，与设备端按通道计数
     * 的口径保持一致，避免设备端按通道计数时因口径不一致而拒绝推送、导致窗口被标记降级（离线）。
     *
     * @param cells            大屏单元列表
     * @param existingWindows  已有窗口
     * @param ignoreWindowId   校验更新时需排除的窗口 ID，创建时为 null
     * @param wx, wy, ww, wh   待校验窗口矩形
     */
    private void validateMaxWindows(List<CellVO> cells, List<ScreenWindowVO> existingWindows,
                                    String ignoreWindowId,
                                    int wx, int wy, int ww, int wh, int cellW, int cellH) {
        java.util.Map<String, Integer> channelMax = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> channelDesc = new java.util.LinkedHashMap<>();
        for (CellVO cell : cells) {
            if (cell.getDeviceId() == null || cell.getChannelName() == null || cell.getMaxWindows() == null) continue;
            String key = cell.getDeviceId() + "|" + cell.getChannelName();
            channelMax.putIfAbsent(key, cell.getMaxWindows());
            channelDesc.putIfAbsent(key, cell.getDeviceName() + "/" + cell.getChannelName());
        }

        for (java.util.Map.Entry<String, Integer> entry : channelMax.entrySet()) {
            String key = entry.getKey();
            int maxWindows = entry.getValue();

            if (!coversChannel(cells, key, wx, wy, ww, wh, cellW, cellH)) continue;

            int count = 0;
            for (ScreenWindowVO existing : existingWindows) {
                if (ignoreWindowId != null && ignoreWindowId.equals(existing.getWindowId())) continue;
                if (coversChannel(cells, key,
                        existing.getX() != null ? existing.getX() : 0,
                        existing.getY() != null ? existing.getY() : 0,
                        existing.getWidth() != null ? existing.getWidth() : 960,
                        existing.getHeight() != null ? existing.getHeight() : 540,
                        cellW, cellH)) {
                    count++;
                }
            }

            if (count + 1 > maxWindows) {
                throw new RuntimeException("通道 [" + channelDesc.get(key)
                        + "] 窗口数已达上限（" + maxWindows + "）");
            }
        }
    }

    /**
     * 判断窗口是否覆盖指定输出通道（该通道下任一单元被覆盖即视为覆盖，计 1 个子窗口）。
     */
    private boolean coversChannel(List<CellVO> cells, String channelKey,
                                  int wx, int wy, int ww, int wh, int cellW, int cellH) {
        for (CellVO cell : cells) {
            if (cell.getDeviceId() == null || cell.getChannelName() == null) continue;
            if (!channelKey.equals(cell.getDeviceId() + "|" + cell.getChannelName())) continue;
            if (windowCoversCell(wx, wy, ww, wh, cell.getRowIndex(), cell.getColIndex(), cellW, cellH)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据输入通道名前缀推断信号源类型。
     */
    private String inferSourceType(String channelName) {
        if (channelName == null) return "";
        String upper = channelName.toUpperCase();
        if (upper.startsWith("HDMI")) return "HDMI";
        if (upper.startsWith("VGA")) return "VGA";
        if (upper.startsWith("DP")) return "DP";
        if (upper.startsWith("SDI")) return "SDI";
        return "Stream";
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
     * 将窗口按覆盖单元拆分并同步到各设备。
     * <p>
     * Phase 1（更新时）：关闭旧单元覆盖设备上的旧子窗口和信号源设备上的旧窗口。
     * Phase 2：为每个覆盖单元创建子窗口（跳过离线设备）。
     * Phase 3：推送完整窗口到输入设备（信号源）。
     * <p>
     * 任一设备离线则标记 degraded=1，由定时重试在设备恢复后补推。
     *
     * @param sw            窗口（含最新位置/大小）
     * @param screen        大屏
     * @param oldCoverages  更新前的覆盖单元列表；为 null 表示新建窗口
     */
    private void syncToCoveredDevices(ScreenWindow sw, Screen screen, List<CellCoverage> oldCoverages) {
        List<CellCoverage> newCoverages = calcCoverages(sw, screen);
        if (newCoverages.isEmpty()) return;

        boolean isUpdate = oldCoverages != null;
        boolean anyOffline = false;
        int successCount = 0;

        // Phase 1: 更新时先关闭旧位置覆盖的设备上的子窗口（只关旧覆盖的设备，不关整个大屏所有设备）
        if (isUpdate) {
            java.util.Set<Long> closedDevices = new java.util.HashSet<>();
            for (CellCoverage cov : oldCoverages) {
                if (closedDevices.contains(cov.deviceId)) continue;
                closedDevices.add(cov.deviceId);
                DevicePageVO dev = deviceMapper.findById(cov.deviceId);
                if (dev != null && dev.getOnline() != null && dev.getOnline() == 1
                        && (dev.getEnabled() == null || dev.getEnabled() == 1)) {
                    try { deviceDriver.closeWindow(buildEndpoint(dev), sw.getWindowId()); } catch (Exception ignored) {}
                }
            }
        }

        // Phase 2: 为每个覆盖单元创建子窗口（跳过离线或禁用设备）
        for (CellCoverage cov : newCoverages) {
            DevicePageVO dev = deviceMapper.findById(cov.deviceId);
            if (dev == null) continue;

            boolean deviceOnline = dev.getOnline() != null && dev.getOnline() == 1;
            boolean deviceEnabled = dev.getEnabled() == null || dev.getEnabled() == 1;
            if (!deviceOnline || !deviceEnabled) {
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
                simWindow.setSourceType(inferSourceType(sw.getChannelName()));
                simWindow.setSourceUrl(sw.getSourceUrl());

                Result<SimWindow> result = deviceDriver.createWindow(endpoint, simWindow);
                if (result != null && result.getCode() == 1) {
                    successCount++;
                }
            } catch (Exception ignored) {
                anyOffline = true;
            }
        }

        // Phase 3: 推送完整窗口快照到输入设备（信号源）
        // 计算实际同步目标数——仅统计在线设备，离线设备不计入 totalTargets
        int onlineOutputDevices = 0;
        for (CellCoverage cov : newCoverages) {
            DevicePageVO dev = deviceMapper.findById(cov.deviceId);
            if (dev != null && dev.getOnline() != null && dev.getOnline() == 1
                    && (dev.getEnabled() == null || dev.getEnabled() == 1)) {
                onlineOutputDevices++;
            }
        }
        DevicePageVO sourceDev = deviceMapper.findById(sw.getDeviceId());
        boolean sourceOnline = sourceDev != null && sourceDev.getOnline() != null && sourceDev.getOnline() == 1;
        boolean sourceEnabled = sourceDev == null || sourceDev.getEnabled() == null || sourceDev.getEnabled() == 1;
        int totalTargets = onlineOutputDevices + (sourceOnline && sourceEnabled ? 1 : 0);

        if (sourceDev != null) {
            if (sourceOnline && sourceEnabled) {
                try {
                    Result<Void> result = deviceDriver.notifyWindow(
                            buildEndpoint(sourceDev), buildWindowSnapshot(sw.getDeviceId(), null));
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
        // 关键：即使所有在线设备都同步成功，只要有离线设备未推送，就必须保持 degraded=1
        if (!anyOffline && totalTargets > 0 && successCount == totalTargets) {
            windowMapper.updateDegraded(sw.getWindowId(), "synced", 0);
        } else {
            windowMapper.updateDegraded(sw.getWindowId(), "pending", 1);
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

            // 重试：先关闭旧子窗口再重建（使用当前位置作为 oldCoverages 以触发 Phase 1 清理）
            syncToCoveredDevices(current, screen, calcCoverages(current, screen));
        }
    }

    // ==================== 设备恢复后窗口重同步 ====================

    /**
     * 设备恢复上线后，将其相关窗口标记为 pending，由定时重试自动补推。
     * <p>
     * 输入设备：标记其信号源窗口为 pending；
     * 输出设备：标记该设备所在大屏的所有窗口为 pending。
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
     * 设备离线后，将其相关窗口标记为 failed + 降级。
     * <p>
     * 输入设备：标记其信号源窗口为 failed + degraded=1；
     * 输出设备：仅标记覆盖了该设备单元的那些窗口，不影响同大屏其他窗口。
     */
    @Override
    public void markFailedForDevice(DevicePageVO device) {
        if ("INPUT".equals(device.getDeviceCategory())) {
            windowMapper.markFailedByDeviceId(device.getId(), "failed", 1);
        } else {
            // 输出设备离线：只标记覆盖了该设备单元的那些窗口，而非整个大屏的所有窗口
            List<Long> screenIds = screenMapper.findScreenIdsByDeviceId(device.getId());
            if (screenIds != null) {
                for (Long screenId : screenIds) {
                    markWindowsOnDeviceCells(screenId, device.getId());
                }
            }
        }
    }

    /**
     * 将覆盖了指定输出设备单元且处于 synced 状态的窗口标记为 failed + 降级。
     * <p>
     * 与 {@link #markPendingForDevice} 配对使用：
     * 设备离线时只影响真正依赖该设备的窗口，不影响同大屏上其他窗口。
     */
    private void markWindowsOnDeviceCells(Long screenId, Long deviceId) {
        Screen screen = screenMapper.findById(screenId);
        if (screen == null) return;

        List<CellVO> cells = screenMapper.findCellsByScreenId(screenId);
        List<ScreenWindowVO> windows = windowMapper.findByScreenId(screenId);

        int cellW = screen.getCellWidth();
        int cellH = screen.getCellHeight();

        for (ScreenWindowVO win : windows) {
            if (!"synced".equals(win.getSyncStatus())) continue;

            for (CellVO cell : cells) {
                if (!deviceId.equals(cell.getDeviceId())) continue;
                if (windowCoversCell(
                        win.getX() != null ? win.getX() : 0,
                        win.getY() != null ? win.getY() : 0,
                        win.getWidth() != null ? win.getWidth() : 960,
                        win.getHeight() != null ? win.getHeight() : 540,
                        cell.getRowIndex(), cell.getColIndex(), cellW, cellH)) {
                    windowMapper.updateDegraded(win.getWindowId(), "failed", 1);
                    break; // 找到一个重叠单元就足够
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
     * 构建输入设备的完整窗口快照。
     * <p>
     * 查询该输入设备（deviceId）在管控系统中记录的所有窗口并转换为 SimWindow 列表；
     * excludeWindowId 用于关闭场景排除待删窗口（此时 DB 尚未删除）。
     */
    private List<SimWindow> buildWindowSnapshot(Long deviceId, String excludeWindowId) {
        List<ScreenWindow> windows = windowMapper.findByDeviceId(deviceId);
        List<SimWindow> snapshot = new ArrayList<>();
        for (ScreenWindow w : windows) {
            if (excludeWindowId != null && excludeWindowId.equals(w.getWindowId())) continue;
            SimWindow simWindow = new SimWindow();
            simWindow.setWindowId(w.getWindowId());
            simWindow.setChannelName(w.getChannelName());
            simWindow.setX(w.getX());
            simWindow.setY(w.getY());
            simWindow.setWidth(w.getWidth());
            simWindow.setHeight(w.getHeight());
            simWindow.setSourceUrl(w.getSourceUrl());
            if (w.getCreateTime() != null) {
                simWindow.setCreateTime(w.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            }
            snapshot.add(simWindow);
        }
        return snapshot;
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