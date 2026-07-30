package com.example.demo.mapper;

import com.example.demo.common.DevicePageDTO;
import com.example.demo.common.DevicePageVO;
import com.example.demo.model.SimDeviceInfo;
import com.github.pagehelper.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备管理 Mapper
 */
@Mapper
public interface DeviceMapper {

    /**
     * 新增设备
     *
     * @param device  设备描述信息
     * @param baseUrl 设备 REST API 基地址
     * @return 影响行数
     */
    int insert(@Param("device") SimDeviceInfo device, @Param("baseUrl") String baseUrl);

    /**
     * 按主键删除设备
     */
    int deleteById(@Param("id") Long id);

    /**
     * 更新启用/禁用状态
     */
    int updateEnabled(@Param("id") Long id, @Param("enabled") Integer enabled);

    /**
     * 更新在线状态与心跳时间
     */
    int updateOnline(@Param("id") Long id,
                     @Param("online") Integer online,
                     @Param("lastHeartbeat") LocalDateTime lastHeartbeat);

    /**
     * 刷新设备信息（更新设备描述字段）
     */
    int updateDeviceInfo(@Param("id") Long id, @Param("device") SimDeviceInfo device);

    /**
     * 按主键查询，返回完整设备信息（含 baseUrl、online）
     */
    DevicePageVO findById(@Param("id") Long id);

    /**
     * 按 baseUrl 查询，返回完整设备信息（含 baseUrl、online）
     */
    DevicePageVO findByBaseUrl(@Param("baseUrl") String baseUrl);

    /**
     * 查询所有设备，用于心跳遍历
     */
    List<DevicePageVO> findAll();

    /**
     * 分页查询设备
     */
    Page<DevicePageVO> pageQuery(DevicePageDTO pageDTO);

}
