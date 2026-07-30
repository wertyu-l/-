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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 添加用户
     * @param user 用户对象
     */
    @Override
    public void addUser(User user) {
        User existing = userMapper.findByUsername(user.getUsername());
        if (existing != null) {
            throw new RuntimeException("用户名已存在");
        }
        User a = new User();
        BeanUtils.copyProperties(user, a);
        // BCrypt 加密密码
        a.setPassword(passwordEncoder.encode(user.getPassword()));
        a.setValidUntil(LocalDateTime.now().plusMonths(6));
        userMapper.insert(a);
    }
    /**
     * 修改用户
     * @param user 用户对象
     */
    @Override
    public void update(User user) {
        // 先查出原记录，防止密码被清空
        User existing = userMapper.findById(user.getId());
        if (existing == null) {
            throw new RuntimeException("用户不存在");
        }
        User a = new User();
        BeanUtils.copyProperties(user, a);
        // 如果传入了新密码，则加密后存储；否则保留原密码
        if (StringUtils.hasText(user.getPassword())) {
            a.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            a.setPassword(existing.getPassword());
        }
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
        if (user.getIsEnabled() != null && user.getIsEnabled() == 1) {
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
        User user = userMapper.findById(id);
        if (user != null) {
            user.setPassword(null);
        }
        return user;
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
        // 不返回密码给调用方
        if (records != null) {
            records.forEach(user -> user.setPassword(null));
        }
        return new PageResult(total, records);
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
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }
        if (user.getIsEnabled() != null && user.getIsEnabled() != 1) {
            throw new RuntimeException("用户已被禁用，请联系管理员");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        return JwtUtils.generateToken(claims);
    }

}