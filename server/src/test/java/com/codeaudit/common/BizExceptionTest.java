package com.codeaudit.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BizExceptionTest {

    @Test
    void shouldSetDefaultCodeTo400() {
        BizException ex = new BizException("参数错误");
        assertEquals(400, ex.getCode());
        assertEquals("参数错误", ex.getMessage());
    }

    @Test
    void shouldSetCustomCode() {
        BizException ex = new BizException(404, "资源不存在");
        assertEquals(404, ex.getCode());
        assertEquals("资源不存在", ex.getMessage());
    }

    @Test
    void shouldInheritFromRuntimeException() {
        BizException ex = new BizException("test");
        assertInstanceOf(RuntimeException.class, ex);
    }
}
