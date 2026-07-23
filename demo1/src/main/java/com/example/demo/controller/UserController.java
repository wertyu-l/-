package com.example.demo.controller;

import com.example.demo.ST.User;
import com.example.demo.common.PageResult;
import com.example.demo.common.Result;
import com.example.demo.common.PageDTO;
import com.example.demo.common.LoginDTO;
import com.example.demo.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 新增用户
     * @param user 用户对象
     * @return
     */
    @PostMapping
    public Result addUser(@RequestBody User user) {
        userService.addUser(user);
        return Result.success();
    }

    /**
     * 根据id查询用户
     * @param id 用户id
     * @return
     */
    @GetMapping("/{id}")
    public Result getUserById(@PathVariable Long id) {
        User user = userService.getUserById(id);
        return Result.success(user);
    }
    /**
    * 修改用户
    * @param user 用户对象
    * @return
    */
   @PutMapping
    public Result update(@RequestBody User user){
        userService.update(user);
        return Result.success();
   }
    /**
     *
     * 删除用户
     * @param username 用户名
     * @return
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
     * 分页查询
     * @param pageDTO 分页查询参数
     * @return
     */
    @PostMapping("/page")
    public Result<PageResult> getPage(@RequestBody PageDTO pageDTO) {
         PageResult pageResult = userService.getPage(pageDTO);
        return Result.success(pageResult);
    }
    /**
     * 退出功能
     *
     */
    @PostMapping("/logout")
    public Result<String> logout() {
        return Result.success();
    }
    /**
     * 登录功能
     * @param loginDTO 登录参数（用户名、密码）
     * @return JWT令牌
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