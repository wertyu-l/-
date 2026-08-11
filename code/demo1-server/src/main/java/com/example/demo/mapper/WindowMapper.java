package com.example.demo.mapper;

import com.example.demo.common.ScreenWindowVO;
import com.example.demo.entity.ScreenWindow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 窗口 Mapper
 */
@Mapper
public interface WindowMapper {

    /** 插入窗口 */
    int insert(ScreenWindow window);

    /** 按 windowId 查询 */
    ScreenWindow findByWindowId(@Param("windowId") String windowId);

    /** 查询大屏下所有窗口（含设备名） */
    List<ScreenWindowVO> findByScreenId(@Param("screenId") Long screenId);

    /** 更新窗口位置/大小 */
    int updatePosition(@Param("windowId") String windowId,
                       @Param("x") Integer x, @Param("y") Integer y,
                       @Param("width") Integer width, @Param("height") Integer height);

    /** 更新同步状态 */
    int updateSyncStatus(@Param("windowId") String windowId,
                         @Param("syncStatus") String syncStatus);

    /** 更新降级状态 */
    int updateDegraded(@Param("windowId") String windowId,
                       @Param("syncStatus") String syncStatus,
                       @Param("degraded") Integer degraded);

    /** 删除窗口 */
    int deleteByWindowId(@Param("windowId") String windowId);

    /** 删除大屏下所有窗口 */
    int deleteByScreenId(@Param("screenId") Long screenId);

    /** 按同步状态查询（用于定时重试） */
    List<ScreenWindow> findBySyncStatus(@Param("statuses") List<String> statuses);

    /** 按输入设备标记窗口同步状态（仅标记 synced 的，避免重复） */
    int updateSyncStatusByDeviceId(@Param("deviceId") Long deviceId,
                                   @Param("syncStatus") String syncStatus);

    /** 按屏幕标记所有窗口同步状态（仅标记 synced 的） */
    int updateSyncStatusByScreenId(@Param("screenId") Long screenId,
                                   @Param("syncStatus") String syncStatus);

    /** 设备离线：按输入设备标记窗口为失败+降级 */
    int markFailedByDeviceId(@Param("deviceId") Long deviceId,
                             @Param("syncStatus") String syncStatus,
                             @Param("degraded") Integer degraded);

    /** 设备离线：按屏幕标记窗口为失败+降级 */
    int markFailedByScreenId(@Param("screenId") Long screenId,
                             @Param("syncStatus") String syncStatus,
                             @Param("degraded") Integer degraded);

}