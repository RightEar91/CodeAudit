package com.codeaudit.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilsTest {

    private static final String SECRET = "Test-Secret-Key-Must-Be-At-Least-256-Bits-Long-Enough-For-HS256";
    private final JwtUtils jwtUtils = new JwtUtils(SECRET, 3600000);

    @Test
    void shouldGenerateAndParseToken() {
        String token = jwtUtils.generateToken(1L, "admin", "ADMIN");
        assertNotNull(token);

        Claims claims = jwtUtils.parseToken(token);
        assertEquals(1L, jwtUtils.getUserId(claims));
        assertEquals("admin", jwtUtils.getUsername(claims));
        assertEquals("ADMIN", jwtUtils.getRole(claims));
    }

    @Test
    void shouldGenerateTokenWithDeveloperRole() {
        String token = jwtUtils.generateToken(2L, "user", "DEVELOPER");
        Claims claims = jwtUtils.parseToken(token);
        assertEquals(2L, jwtUtils.getUserId(claims));
        assertEquals("DEVELOPER", jwtUtils.getRole(claims));
    }

    @Test
    void shouldFailOnExpiredToken() throws InterruptedException {
        JwtUtils shortLived = new JwtUtils(SECRET, 1);
        String token = shortLived.generateToken(1L, "admin", "ADMIN");
        Thread.sleep(10);
        assertThrows(ExpiredJwtException.class, () -> shortLived.parseToken(token));
    }

    @Test
    void shouldFailOnTamperedToken() {
        String token = jwtUtils.generateToken(1L, "admin", "ADMIN");
        String tampered = token.substring(0, token.length() - 4) + "xxxx";
        assertThrows(JwtException.class, () -> jwtUtils.parseToken(tampered));
    }

    @Test
    void shouldFailOnEmptyOrInvalidToken() {
        assertThrows(RuntimeException.class, () -> jwtUtils.parseToken(""));
        assertThrows(RuntimeException.class, () -> jwtUtils.parseToken("not.a.jwt"));
    }

    @Test
    void shouldFailOnTokenSignedWithDifferentKey() {
        JwtUtils other = new JwtUtils("Different-Secret-Key-Must-Be-At-Least-256-Bits-For-Testing", 3600000);
        String token = other.generateToken(1L, "admin", "ADMIN");
        assertThrows(JwtException.class, () -> jwtUtils.parseToken(token));
    }

    @Test
    void shouldGenerateDifferentTokensForDifferentUsers() {
        String t1 = jwtUtils.generateToken(1L, "admin", "ADMIN");
        String t2 = jwtUtils.generateToken(2L, "user", "DEVELOPER");
        assertNotEquals(t1, t2);
    }
}
