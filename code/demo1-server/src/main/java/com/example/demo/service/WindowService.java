package com.example.demo.service;

import com.example.demo.common.DevicePageVO;
import com.example.demo.common.OutputDeviceWindowsVO;
import com.example.demo.common.ScreenWindowVO;
import com.example.demo.common.WindowCreateRequest;
import com.example.demo.common.WindowUpdateRequest;

import java.util.List;

/**
 * 窗口管理 Service 接口
 */
public interface WindowService {

    /** 创建窗口（双写：DB + 转发设备） */
    ScreenWindowVO createWindow(Long screenId, WindowCreateRequest request);

    /** 更新窗口位置/大小（双写：DB + 转发设备） */
    ScreenWindowVO updateWindow(Long screenId, String windowId, WindowUpdateRequest request);

    /** 关闭窗口（先同步设备，再删 DB） */
    void closeWindow(Long screenId, String windowId);

    /** 查询大屏下所有窗口 */
    List<ScreenWindowVO> getWindows(Long screenId);

    /** 一键清空大屏窗口 */
    void clearWindows(Long screenId);

    /** 查询大屏各输出设备的窗口信息 */
    List<OutputDeviceWindowsVO> getOutputDeviceWindows(Long screenId);

    /** 设备恢复上线后，标记相关窗口为 pending 以触发重同步 */
    void markPendingForDevice(DevicePageVO device);

}