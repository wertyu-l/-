package com.example.demo.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Result 统一返回结果单元测试
 * <p>
 * 覆盖 success()、success(T)、error(String) 三个静态工厂方法，
 * 验证 code/msg/data 字段值的正确性。
 */
class ResultTest {

    // ========== success() ==========

    /**
     * 无参 success() 应返回 code=1，msg 和 data 为 null
     */
    @Test
    void success_noArg_shouldReturnCodeOne() {
        Result<Object> result = Result.success();
        assertEquals(1, result.getCode());
        assertNull(result.getMsg());
        assertNull(result.getData());
    }

    /**
     * 带参 success(T) 应返回 code=1 且 data 为传入对象
     */
    @Test
    void success_withData_shouldReturnCodeOneAndData() {
        String data = "hello";
        Result<String> result = Result.success(data);
        assertEquals(1, result.getCode());
        assertEquals("hello", result.getData());
        assertNull(result.getMsg());
    }

    /**
     * 带参 success(T) 传入 null 时 data 应为 null
     */
    @Test
    void success_withNullData_shouldReturnCodeOneAndNullData() {
        Result<String> result = Result.success(null);
        assertEquals(1, result.getCode());
        assertNull(result.getData());
        assertNull(result.getMsg());
    }

    // ========== error() ==========

    /**
     * error(String) 应返回 code=0，msg 为传入的错误信息
     */
    @Test
    void error_shouldReturnCodeZeroAndMsg() {
        Result<Object> result = Result.error("操作失败");
        assertEquals(0, result.getCode());
        assertEquals("操作失败", result.getMsg());
        assertNull(result.getData());
    }

    /**
     * error(String) 传入空字符串时 msg 应为空字符串
     */
    @Test
    void error_emptyMsg_shouldReturnEmptyMsg() {
        Result<Object> result = Result.error("");
        assertEquals(0, result.getCode());
        assertEquals("", result.getMsg());
        assertNull(result.getData());
    }

    /**
     * error(String) 传入 null 时 msg 应为 null
     */
    @Test
    void error_nullMsg_shouldReturnNullMsg() {
        Result<Object> result = Result.error(null);
        assertEquals(0, result.getCode());
        assertNull(result.getMsg());
        assertNull(result.getData());
    }

    // ========== 泛型兼容 ==========

    /**
     * 不同泛型类型的 Result 应正确保留类型
     */
    @Test
    void genericType_shouldBePreserved() {
        Result<Integer> intResult = Result.success(42);
        assertEquals(Integer.valueOf(42), intResult.getData());

        Result<String> strResult = Result.success("hello");
        assertEquals("hello", strResult.getData());
    }

    /**
     * error 返回的 Result 应兼容任意泛型
     */
    @Test
    void error_genericType_shouldBeCompatible() {
        Result<Integer> result = Result.error("错误");
        assertEquals(0, result.getCode());
        assertEquals("错误", result.getMsg());
        assertNull(result.getData());
    }
}