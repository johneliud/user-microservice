package io.github.johneliud.user_microservice.util;

import io.github.johneliud.user_microservice.entity.Role;
import io.github.johneliud.user_microservice.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    private static final String SECRET = "NqnGzaDEIZhGXWnbnWDHViZyKhinshBQ123456";
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiration", 86400000L);
        ReflectionTestUtils.setField(jwtUtil, "MFA_TOKEN_EXPIRATION", 300000L);
    }

    private User buildUser(Role role) {
        return User.builder()
                .id(USER_ID)
                .username("johndoe")
                .email("john@example.com")
                .passwordHash("hashed")
                .roles(Set.of(role))
                .build();
    }

    // ── generateToken ─────────────────────────────────────────────────────────

    @Test
    void generateToken_containsUserIdAndRole() {
        String token = jwtUtil.generateToken(buildUser(Role.ROLE_USER));
        Claims claims = jwtUtil.validateToken(token);

        assertEquals(USER_ID.toString(), claims.getSubject());
        assertEquals("ROLE_USER", claims.get("role", String.class));
    }

    @Test
    void generateToken_adminRole_claimsRoleAdmin() {
        String token = jwtUtil.generateToken(buildUser(Role.ROLE_ADMIN));
        Claims claims = jwtUtil.validateToken(token);

        assertEquals("ROLE_ADMIN", claims.get("role", String.class));
    }

    @Test
    void generateToken_isNotMfaToken() {
        String token = jwtUtil.generateToken(buildUser(Role.ROLE_USER));
        Claims claims = jwtUtil.validateToken(token);

        assertNull(claims.get("scope", String.class));
    }

    // ── generateMfaToken ──────────────────────────────────────────────────────

    @Test
    void generateMfaToken_containsUserIdAndMfaScope() {
        String token = jwtUtil.generateMfaToken(buildUser(Role.ROLE_USER));
        Claims claims = jwtUtil.validateToken(token);

        assertEquals(USER_ID.toString(), claims.getSubject());
        assertEquals("mfa", claims.get("scope", String.class));
    }

    @Test
    void generateMfaToken_doesNotContainRoleClaim() {
        String token = jwtUtil.generateMfaToken(buildUser(Role.ROLE_USER));
        Claims claims = jwtUtil.validateToken(token);

        assertNull(claims.get("role", String.class));
    }

    // ── validateMfaToken ──────────────────────────────────────────────────────

    @Test
    void validateMfaToken_validMfaToken_returnsClaims() {
        String token = jwtUtil.generateMfaToken(buildUser(Role.ROLE_USER));
        Claims claims = jwtUtil.validateMfaToken(token);

        assertEquals(USER_ID.toString(), claims.getSubject());
    }

    @Test
    void validateMfaToken_regularAccessToken_throwsException() {
        String token = jwtUtil.generateToken(buildUser(Role.ROLE_USER));

        assertThrows(IllegalArgumentException.class, () -> jwtUtil.validateMfaToken(token));
    }

    // ── validateToken (expired) ───────────────────────────────────────────────

    @Test
    void validateToken_expiredToken_throwsExpiredJwtException() {
        ReflectionTestUtils.setField(jwtUtil, "expiration", -1000L);
        String token = jwtUtil.generateToken(buildUser(Role.ROLE_USER));

        assertThrows(ExpiredJwtException.class, () -> jwtUtil.validateToken(token));
    }

    @Test
    void validateToken_tamperedToken_throwsException() {
        String token = jwtUtil.generateToken(buildUser(Role.ROLE_USER));
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertThrows(Exception.class, () -> jwtUtil.validateToken(tampered));
    }

    // ── helper methods ────────────────────────────────────────────────────────

    @Test
    void getUserId_returnsSubject() {
        String token = jwtUtil.generateToken(buildUser(Role.ROLE_USER));
        Claims claims = jwtUtil.validateToken(token);

        assertEquals(USER_ID.toString(), jwtUtil.getUserId(claims));
    }

    @Test
    void getRole_returnsRoleClaim() {
        String token = jwtUtil.generateToken(buildUser(Role.ROLE_USER));
        Claims claims = jwtUtil.validateToken(token);

        assertEquals("ROLE_USER", jwtUtil.getRole(claims));
    }

    @Test
    void getExpiration_returnsConfiguredValue() {
        assertEquals(86400000L, jwtUtil.getExpiration());
    }
}