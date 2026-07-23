package com.example.demo.service.impl;

import com.example.demo.ST.User;
import com.example.demo.common.PageResult;
import com.example.demo.common.PageDTO;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.UserService;
import com.example.demo.utils.JwtUtils;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    /**
     * @param user 用户对象
     */
    @Override
    public void addUser(User user) {
        User a = new User();
        BeanUtils.copyProperties(user, a);
        a.setValidUntil(LocalDateTime.now().plusMonths(6));
        userMapper.insert(a);
    }
    /**
     * 修改用户
     * @param user 用户对象
     */
    @Override
    public void update(User user) {
        User a = new User();
        BeanUtils.copyProperties(user, a);
        userMapper.updateById(a);
    }
    /**
     * 删除用户
     * @param username 用户名
     */
    @Override
    public void deleteByUsername(String username) {
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if ("admin".equals(user.getRole())) {
            throw new RuntimeException("管理员用户不允许删除");
        }
        if (user.getIsEnabled() != null && user.getIsEnabled()) {
            throw new RuntimeException("启用的用户不能删除");
        }
        userMapper.deleteByUsername(username);
    }

    /**
     * 根据id查询用户
     * @param id 用户id
     * @return
     */
    @Override
    public User getUserById(Long id) {
        return userMapper.findById(id);

    }

    /**
     * 分页查询
     * @param pageDTO 分页查询参数
     * @return
     */
    @Override
    public PageResult getPage(PageDTO pageDTO) {
        PageHelper.startPage(pageDTO.getPage(), pageDTO.getPageSize());


        Page<User> page = userMapper.pageQuery(pageDTO);

        long total = page.getTotal();
        List<User> records = page.getResult();
        return  new PageResult(total,records);
    }

    /**
     * 登录
     * @param username 用户名
     * @param password 密码
     * @return JWT令牌
     */
    @Override
    public String login(String username, String password) {
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }
        if (!password.equals(user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }
        if (user.getIsEnabled() != null && !user.getIsEnabled()) {
            throw new RuntimeException("用户已被禁用，请联系管理员");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        return JwtUtils.generateToken(claims);
    }

}