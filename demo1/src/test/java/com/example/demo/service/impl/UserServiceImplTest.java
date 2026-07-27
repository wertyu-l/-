package com.example.demo.service.impl;

import com.example.demo.ST.User;
import com.example.demo.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setPassword("plainPassword123");
        testUser.setRole("user");
        testUser.setIsEnabled(1);
    }

    /**
     * 测试添加用户时密码被 BCrypt 加密
     */
    @Test
    void addUser_ShouldEncodePassword() {
        when(userMapper.findByUsername("testuser")).thenReturn(null);
        when(passwordEncoder.encode("plainPassword123")).thenReturn("$2a$10$encodedHash");

        userService.addUser(testUser);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        User insertedUser = userCaptor.getValue();

        assertEquals("$2a$10$encodedHash", insertedUser.getPassword());
        assertNotEquals("plainPassword123", insertedUser.getPassword());
    }

    /**
     * 测试用户名已存在时抛异常
     */
    @Test
    void addUser_ShouldThrowWhenUsernameExists() {
        when(userMapper.findByUsername("testuser")).thenReturn(testUser);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.addUser(testUser));
        assertEquals("用户名已存在", ex.getMessage());
        verify(userMapper, never()).insert(any());
    }

    /**
     * 测试登录时使用 BCrypt 密码匹配成功
     */
    @Test
    void login_ShouldReturnTokenWhenBCryptMatches() {
        testUser.setPassword("$2a$10$storedHash");
        when(userMapper.findByUsername("testuser")).thenReturn(testUser);
        when(passwordEncoder.matches("plainPassword123", "$2a$10$storedHash")).thenReturn(true);

        String token = userService.login("testuser", "plainPassword123");

        assertNotNull(token);
        verify(userMapper, never()).updateById(any());
    }

    /**
     * 测试 BCrypt 匹配失败且明文密码不匹配时抛异常
     */
    @Test
    void login_ShouldThrowWhenPasswordMismatch() {
        testUser.setPassword("$2a$10$storedHash");
        when(userMapper.findByUsername("testuser")).thenReturn(testUser);
        when(passwordEncoder.matches("wrongPassword", "$2a$10$storedHash")).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.login("testuser", "wrongPassword"));
        assertEquals("用户名或密码错误", ex.getMessage());
    }

    /**
     * 测试用户被禁用时抛异常
     */
    @Test
    void login_ShouldThrowWhenUserDisabled() {
        testUser.setPassword("$2a$10$storedHash");
        testUser.setIsEnabled(0);
        when(userMapper.findByUsername("testuser")).thenReturn(testUser);
        when(passwordEncoder.matches("plainPassword123", "$2a$10$storedHash")).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.login("testuser", "plainPassword123"));
        assertEquals("用户已被禁用，请联系管理员", ex.getMessage());
    }

    /**
     * 测试根据id查询用户时，不返回密码
     */
    @Test
    void getUserById_ShouldClearPassword() {
        testUser.setPassword("$2a$10$storedHash");
        when(userMapper.findById(1L)).thenReturn(testUser);

        User result = userService.getUserById(1L);

        assertNotNull(result);
        assertNull(result.getPassword());
    }

    /**
     * 测试修改用户时，提供新密码则加密
     */
    @Test
    void update_ShouldEncodeNewPassword() {
        User existing = new User();
        existing.setPassword("$2a$10$oldHash");
        when(userMapper.findById(testUser.getId())).thenReturn(existing);

        testUser.setPassword("newPassword456");
        when(passwordEncoder.encode("newPassword456")).thenReturn("$2a$10$newHash");

        userService.update(testUser);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(userCaptor.capture());
        User updatedUser = userCaptor.getValue();

        assertEquals("$2a$10$newHash", updatedUser.getPassword());
    }

    /**
     * 测试修改用户时，不传密码则保留原密码不变
     */
    @Test
    void update_ShouldKeepOldPasswordWhenPasswordIsNull() {
        User existing = new User();
        existing.setPassword("$2a$10$oldHash");
        when(userMapper.findById(testUser.getId())).thenReturn(existing);

        testUser.setPassword(null);

        userService.update(testUser);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(userCaptor.capture());
        User updatedUser = userCaptor.getValue();

        assertEquals("$2a$10$oldHash", updatedUser.getPassword());
        verify(passwordEncoder, never()).encode(any());
    }

    /**
     * 测试修改用户时，空字符串密码保留原密码
     */
    @Test
    void update_ShouldKeepOldPasswordWhenPasswordIsEmpty() {
        User existing = new User();
        existing.setPassword("$2a$10$oldHash");
        when(userMapper.findById(testUser.getId())).thenReturn(existing);

        testUser.setPassword("");

        userService.update(testUser);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(userCaptor.capture());
        User updatedUser = userCaptor.getValue();

        assertEquals("$2a$10$oldHash", updatedUser.getPassword());
        verify(passwordEncoder, never()).encode(any());
    }

    /**
     * 测试修改不存在的用户时抛异常
     */
    @Test
    void update_ShouldThrowWhenUserNotFound() {
        when(userMapper.findById(testUser.getId())).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.update(testUser));
        assertEquals("用户不存在", ex.getMessage());
        verify(userMapper, never()).updateById(any());
    }
}