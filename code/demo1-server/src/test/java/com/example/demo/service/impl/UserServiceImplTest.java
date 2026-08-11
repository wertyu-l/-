package com.example.demo.service.impl;

import com.example.demo.ST.User;
import com.example.demo.common.PageDTO;
import com.example.demo.common.PageResult;
import com.example.demo.mapper.UserMapper;
import com.github.pagehelper.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * UserServiceImpl 单元测试
 * <p>
 * 覆盖用户注册、登录、修改、删除、分页查询的完整流程，
 * 重点验证密码加密、Token 签发、权限校验、异常场景。
 */
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
        testUser.setPassword("plainPassword");
        testUser.setRole("user");
        testUser.setIsEnabled(1);
    }

    // ========== 用户注册 ==========

    /**
     * 注册时应使用 BCrypt 加密密码，并设置有效期（+6个月）
     */
    @Test
    void addUser_shouldEncryptPasswordAndSetExpiry() {
        when(userMapper.findByUsername("testuser")).thenReturn(null);
        when(passwordEncoder.encode("plainPassword")).thenReturn("encryptedPwd");

        userService.addUser(testUser);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User saved = captor.getValue();

        assertEquals("encryptedPwd", saved.getPassword());
        assertNotNull(saved.getValidUntil());
        assertTrue(saved.getValidUntil().isAfter(LocalDateTime.now().plusMonths(5)));
    }

    /**
     * 重复用户名注册应抛出异常
     */
    @Test
    void addUser_duplicateUsername_shouldThrowException() {
        when(userMapper.findByUsername("testuser")).thenReturn(testUser);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.addUser(testUser));
        assertEquals("用户名已存在", ex.getMessage());
        verify(userMapper, never()).insert(any());
    }

    // ========== 用户修改 ==========

    /**
     * 修改时传入新密码，应重新加密
     */
    @Test
    void update_withNewPassword_shouldEncrypt() {
        User existing = new User();
        existing.setId(1L);
        existing.setPassword("oldEncrypted");
        when(userMapper.findById(1L)).thenReturn(existing);
        when(passwordEncoder.encode("newPassword")).thenReturn("newEncrypted");

        testUser.setPassword("newPassword");
        userService.update(testUser);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals("newEncrypted", captor.getValue().getPassword());
    }

    /**
     * 修改时不传密码，应保留原密码
     */
    @Test
    void update_withoutPassword_shouldKeepOldPassword() {
        User existing = new User();
        existing.setId(1L);
        existing.setPassword("oldEncrypted");
        when(userMapper.findById(1L)).thenReturn(existing);

        testUser.setPassword(null);
        userService.update(testUser);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals("oldEncrypted", captor.getValue().getPassword());
    }

    /**
     * 修改不存在的用户应抛出异常
     */
    @Test
    void update_userNotFound_shouldThrowException() {
        when(userMapper.findById(1L)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.update(testUser));
        assertEquals("用户不存在", ex.getMessage());
    }

    // ========== 用户删除 ==========

    /**
     * 禁止删除管理员用户
     */
    @Test
    void deleteByUsername_adminUser_shouldThrowException() {
        User admin = new User();
        admin.setUsername("admin");
        admin.setRole("admin");
        when(userMapper.findByUsername("admin")).thenReturn(admin);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.deleteByUsername("admin"));
        assertEquals("管理员用户不允许删除", ex.getMessage());
    }

    /**
     * 禁止删除已启用的用户（需先禁用）
     */
    @Test
    void deleteByUsername_enabledUser_shouldThrowException() {
        testUser.setIsEnabled(1);
        when(userMapper.findByUsername("testuser")).thenReturn(testUser);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.deleteByUsername("testuser"));
        assertEquals("启用的用户不能删除", ex.getMessage());
    }

    /**
     * 删除不存在的用户应抛出异常
     */
    @Test
    void deleteByUsername_notFound_shouldThrowException() {
        when(userMapper.findByUsername("unknown")).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.deleteByUsername("unknown"));
        assertEquals("用户不存在", ex.getMessage());
    }

    /**
     * 已禁用的普通用户可正常删除
     */
    @Test
    void deleteByUsername_disabledUser_shouldSucceed() {
        testUser.setIsEnabled(0);
        testUser.setRole("user");
        when(userMapper.findByUsername("testuser")).thenReturn(testUser);

        userService.deleteByUsername("testuser");
        verify(userMapper).deleteByUsername("testuser");
    }

    // ========== 用户查询 ==========

    /**
     * 查询用户时应抹除密码字段（安全考虑）
     */
    @Test
    void getUserById_shouldReturnUserWithoutPassword() {
        testUser.setPassword("secret");
        when(userMapper.findById(1L)).thenReturn(testUser);

        User result = userService.getUserById(1L);
        assertNotNull(result);
        assertNull(result.getPassword());
    }

    /**
     * 查询不存在的用户应返回 null
     */
    @Test
    void getUserById_notFound_shouldReturnNull() {
        when(userMapper.findById(999L)).thenReturn(null);

        User result = userService.getUserById(999L);
        assertNull(result);
    }

    /**
     * 分页查询结果中应抹除密码字段
     */
    @SuppressWarnings("unchecked")
    @Test
    void getPage_shouldReturnPageResultWithoutPasswords() {
        PageDTO pageDTO = new PageDTO();
        pageDTO.setPage(1);
        pageDTO.setPageSize(10);

        Page<User> mockPage = mock(Page.class);
        when(mockPage.getTotal()).thenReturn(2L);
        List<User> users = List.of(testUser);
        when(mockPage.getResult()).thenReturn(users);
        when(userMapper.pageQuery(pageDTO)).thenReturn(mockPage);

        PageResult result = userService.getPage(pageDTO);

        assertEquals(2L, result.getTotal());
        @SuppressWarnings("unchecked")
        List<User> records = result.getRecords();
        assertNull(records.get(0).getPassword());
    }

    // ========== 用户登录 ==========

    /**
     * 正常登录应返回 JWT Token
     */
    @Test
    void login_success_shouldReturnToken() {
        when(userMapper.findByUsername("testuser")).thenReturn(testUser);
        when(passwordEncoder.matches("plainPassword", testUser.getPassword())).thenReturn(true);

        String token = userService.login("testuser", "plainPassword");
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    /**
     * 用户名不存在应抛出异常（不暴露具体原因）
     */
    @Test
    void login_userNotFound_shouldThrowException() {
        when(userMapper.findByUsername("unknown")).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.login("unknown", "password"));
        assertEquals("用户名或密码错误", ex.getMessage());
    }

    /**
     * 密码错误应抛出异常
     */
    @Test
    void login_wrongPassword_shouldThrowException() {
        when(userMapper.findByUsername("testuser")).thenReturn(testUser);
        when(passwordEncoder.matches("wrongPassword", testUser.getPassword())).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.login("testuser", "wrongPassword"));
        assertEquals("用户名或密码错误", ex.getMessage());
    }

    /**
     * 已被禁用的用户无法登录
     */
    @Test
    void login_disabledUser_shouldThrowException() {
        testUser.setIsEnabled(0);
        when(userMapper.findByUsername("testuser")).thenReturn(testUser);
        when(passwordEncoder.matches("plainPassword", testUser.getPassword())).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.login("testuser", "plainPassword"));
        assertEquals("用户已被禁用，请联系管理员", ex.getMessage());
    }
}