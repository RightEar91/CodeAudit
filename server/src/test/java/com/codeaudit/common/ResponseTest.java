package com.codeaudit.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResponseTest {

    @Test
    void ok_shouldReturn200WithData() {
        Response<String> res = Response.ok("hello");
        assertEquals(200, res.getCode());
        assertEquals("success", res.getMessage());
        assertEquals("hello", res.getData());
    }

    @Test
    void ok_shouldReturn200WithoutData() {
        Response<Void> res = Response.ok();
        assertEquals(200, res.getCode());
        assertNull(res.getData());
    }

    @Test
    void created_shouldReturn201() {
        Response<String> res = Response.created("entity");
        assertEquals(201, res.getCode());
        assertEquals("entity", res.getData());
    }

    @Test
    void fail_shouldReturnCustomCode() {
        Response<Void> res = Response.fail(404, "找不到");
        assertEquals(404, res.getCode());
        assertEquals("找不到", res.getMessage());
        assertNull(res.getData());
    }

    @Test
    void fail_shouldDefaultTo400() {
        Response<Void> res = Response.fail("参数错误");
        assertEquals(400, res.getCode());
        assertEquals("参数错误", res.getMessage());
    }
}
