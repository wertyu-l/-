package com.example.demo.mapper;

import com.example.demo.common.CellVO;
import com.example.demo.common.ScreenPageDTO;
import com.example.demo.common.ScreenPageVO;
import com.example.demo.entity.Screen;
import com.example.demo.entity.ScreenCell;
import com.github.pagehelper.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 大屏 + 单元 Mapper
 */
@Mapper
public interface ScreenMapper {

    /** 插入大屏，返回自增 id */
    int insertScreen(Screen screen);

    /** 批量插入单元 */
    int insertCells(@Param("cells") List<ScreenCell> cells);

    /** 分页查询大屏列表 */
    Page<ScreenPageVO> pageQuery(ScreenPageDTO dto);

    /** 查询大屏详情 */
    Screen findById(@Param("id") Long id);

    /** 查询大屏所有单元（含设备信息） */
    List<CellVO> findCellsByScreenId(@Param("screenId") Long screenId);

    /** 按 id 查询单个单元 */
    ScreenCell findCellById(@Param("cellId") Long cellId);

    /** 更新单元绑定 */
    int updateCellBinding(@Param("cellId") Long cellId,
                          @Param("deviceId") Long deviceId,
                          @Param("channelName") String channelName);

    /** 统计某设备在所有大屏中的绑定总数 */
    int countDeviceBindings(@Param("deviceId") Long deviceId);

    /** 统计某大屏的窗口数 */
    int countWindowsByScreenId(@Param("screenId") Long screenId);

    /** 删除大屏（级联由 DB 外键处理） */
    int deleteScreen(@Param("id") Long id);

}