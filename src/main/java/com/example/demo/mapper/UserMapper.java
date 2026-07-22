package com.example.demo.mapper;

import com.example.demo.ST.User;
import com.example.demo.common.PageDTO;
import com.github.pagehelper.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    /**
     * 新增用户
     * @param user 用户对象
     * @return 影响行数
     */
    int insert(User user);

    /**
     * 根据id查询用户
     * @param id 用户编号
     * @return 用户对象
     */
    User findById(@Param("id") Long id);

    /**
     * 根据用户名查询用户
     * @param username 用户名
     * @return 用户对象
     */
    User findByUsername(@Param("username") String username);

    /**
     * 根据id更新用户
     * @param user 用户对象
     * @return 影响行数
     */
    int updateById(User user);

    /**
     * 根据用户名删除用户
     * @param username 用户名
     * @return 影响行数
     */
    int deleteByUsername(@Param("username") String username);


    /**
     * 分页查询用户
     * @param pageDTO 分页查询参数
     * @return 分页结果
     */
    Page<User> pageQuery(PageDTO pageDTO);
}