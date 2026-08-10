package com.example.demo.controller;

import com.example.demo.common.*;
import com.example.demo.service.WindowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 窗口管理控制器
 * <p>
 * 基路径：/screen/{screenId}
 */
@RestController
public class WindowController {

    @Autowired
    private WindowService windowService;

    /** 创建窗口：POST /screen/{screenId}/window */
    @PostMapping("/screen/{screenId}/window")
    public Result<ScreenWindowVO> createWindow(@PathVariable Long screenId,
                                               @RequestBody WindowCreateRequest request) {
        try {
            ScreenWindowVO vo = windowService.createWindow(screenId, request);
            return Result.success(vo);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 更新窗口：PUT /screen/{screenId}/window/{windowId} */
    @PutMapping("/screen/{screenId}/window/{windowId}")
    public Result<ScreenWindowVO> updateWindow(@PathVariable Long screenId,
                                               @PathVariable String windowId,
                                               @RequestBody WindowUpdateRequest request) {
        try {
            ScreenWindowVO vo = windowService.updateWindow(screenId, windowId, request);
            return Result.success(vo);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 关闭窗口：DELETE /screen/{screenId}/window/{windowId} */
    @DeleteMapping("/screen/{screenId}/window/{windowId}")
    public Result<Void> closeWindow(@PathVariable Long screenId,
                                    @PathVariable String windowId) {
        try {
            windowService.closeWindow(screenId, windowId);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 查询窗口列表：GET /screen/{screenId}/windows */
    @GetMapping("/screen/{screenId}/windows")
    public Result<List<ScreenWindowVO>> getWindows(@PathVariable Long screenId) {
        try {
            List<ScreenWindowVO> list = windowService.getWindows(screenId);
            return Result.success(list);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 一键清空：DELETE /screen/{screenId}/windows */
    @DeleteMapping("/screen/{screenId}/windows")
    public Result<Void> clearWindows(@PathVariable Long screenId) {
        try {
            windowService.clearWindows(screenId);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 查询各输出设备的窗口信息：GET /screen/{screenId}/output-devices */
    @GetMapping("/screen/{screenId}/output-devices")
    public Result<List<OutputDeviceWindowsVO>> getOutputDeviceWindows(@PathVariable Long screenId) {
        try {
            List<OutputDeviceWindowsVO> list = windowService.getOutputDeviceWindows(screenId);
            return Result.success(list);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

}