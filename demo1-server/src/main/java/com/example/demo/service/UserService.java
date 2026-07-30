package com.example.demo.service;

import com.example.demo.ST.User;
import com.example.demo.common.PageDTO;
import com.example.demo.common.PageResult;

public interface UserService {

    /**
     * 新增用户
     * @param user 用户对象
     */
    void addUser(User user);

    /**
     * 修改用户
     * @param user 用户对象
     */
    void update(User user);

    /**
     * 删除用户
     * @param username 用户名
     */
    void deleteByUsername(String username);

    /**
     * 根据id查询用户
     * @param id 用户id
     * @return 用户对象
     */
    User getUserById(Long id);

    /**
     * 分页查询
     * @param pageDTO 分页查询参数
     * @return 分页查询结果
     */
    PageResult getPage(PageDTO pageDTO);

    /**
     * 登录
     * @param username 用户名
     * @param password 密码
     * @return JWT令牌
     */
    String login(String username, String password);
}