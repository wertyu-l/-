package com.example.demo.controller;

import com.example.demo.ST.User;
import com.example.demo.common.PageResult;
import com.example.demo.common.Result;
import com.example.demo.common.PageDTO;
import com.example.demo.common.LoginDTO;
import com.example.demo.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理控制器
 * <p>
 * 基路径 /user，提供用户注册、登录、增删改查等功能。
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 新增用户
     *
     * @param user 用户对象
     * @return 操作结果
     * @throws RuntimeException 用户名已存在时抛出
     */
    @PostMapping
    public Result addUser(@RequestBody User user) {
        try {
            userService.addUser(user);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 根据 id 查询用户
     *
     * @param id 用户主键
     * @return 用户信息
     */
    @GetMapping("/{id}")
    public Result<User> getUserById(@PathVariable Long id) {
        User user = userService.getUserById(id);
        return Result.success(user);
    }

    /**
     * 修改用户
     *
     * @param user 用户对象（含 id 标识要修改的用户）
     * @return 操作结果
     */
    @PutMapping
    public Result update(@RequestBody User user) {
        userService.update(user);
        return Result.success();
    }

    /**
     * 删除用户
     *
     * @param username 用户名
     * @return 操作结果
     * @throws RuntimeException 用户不存在时抛出
     */
    @DeleteMapping("/{username}")
    public Result deleteByUsername(@PathVariable String username) {
        try {
            userService.deleteByUsername(username);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 分页查询用户列表
     *
     * @param pageDTO 分页查询参数（页码、每页数量）
     * @return 分页结果
     */
    @GetMapping("/page")
    public Result<PageResult> getPage(PageDTO pageDTO) {
        PageResult pageResult = userService.getPage(pageDTO);
        return Result.success(pageResult);
    }

    /**
     * 退出登录
     *
     * @return 操作结果
     */
    @PostMapping("/logout")
    public Result<String> logout() {
        return Result.success();
    }

    /**
     * 登录
     *
     * @param loginDTO 登录参数（用户名、密码）
     * @return JWT 令牌
     * @throws RuntimeException 用户名/密码错误时抛出
     */
    @PostMapping("/login")
    public Result<String> login(@RequestBody LoginDTO loginDTO) {
        try {
            String token = userService.login(loginDTO.getUsername(), loginDTO.getPassword());
            return Result.success(token);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

}