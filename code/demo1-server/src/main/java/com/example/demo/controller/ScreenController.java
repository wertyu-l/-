package com.example.demo.controller;

import com.example.demo.common.*;
import com.example.demo.service.ScreenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 大屏配置控制器
 */
@RestController
@RequestMapping("/screen")
public class ScreenController {

    @Autowired
    private ScreenService screenService;

    /** 创建大屏（自动生成 rows×cols 个单元并绑定设备） */
    @PostMapping
    public Result<ScreenDetailVO> createScreen(@RequestBody ScreenCreateRequest request) {
        try {
            ScreenDetailVO vo = screenService.createScreen(request);
            return Result.success(vo);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 分页查询大屏列表 */
    @GetMapping("/page")
    public Result<PageResult<ScreenPageVO>> getPage(ScreenPageDTO dto) {
        try {
            PageResult<ScreenPageVO> result = screenService.getPage(dto);
            return Result.success(result);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 获取大屏详情（含单元列表） */
    @GetMapping("/{id}")
    public Result<ScreenDetailVO> getDetail(@PathVariable Long id) {
        try {
            ScreenDetailVO vo = screenService.getDetail(id);
            return Result.success(vo);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 删除大屏（级联删除单元和窗口） */
    @DeleteMapping("/{id}")
    public Result<Void> deleteScreen(@PathVariable Long id) {
        try {
            screenService.deleteScreen(id);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 绑定/更换设备通道 */
    @PutMapping("/{screenId}/cell/{cellId}")
    public Result<CellVO> bindCell(@PathVariable Long screenId,
                                   @PathVariable Long cellId,
                                   @RequestBody CellBindRequest request) {
        try {
            CellVO vo = screenService.bindCell(screenId, cellId, request);
            return Result.success(vo);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

}