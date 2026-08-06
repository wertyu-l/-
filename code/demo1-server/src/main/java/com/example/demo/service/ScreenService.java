package com.example.demo.service;

import com.example.demo.common.*;

/**
 * 大屏配置 Service 接口
 */
public interface ScreenService {

    /** 创建大屏（自动生成单元并校验绑定） */
    ScreenDetailVO createScreen(ScreenCreateRequest request);

    /** 分页查询大屏列表 */
    PageResult<ScreenPageVO> getPage(ScreenPageDTO dto);

    /** 获取大屏详情（含单元列表） */
    ScreenDetailVO getDetail(Long id);

    /** 删除大屏（级联删除单元和窗口） */
    void deleteScreen(Long id);

    /** 绑定/更换设备通道 */
    CellVO bindCell(Long screenId, Long cellId, CellBindRequest request);

}