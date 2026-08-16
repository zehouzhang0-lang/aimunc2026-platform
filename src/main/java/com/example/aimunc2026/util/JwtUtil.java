package com.example.aimunc2026.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * ════════════════════════════════════════════════════════════
 *  【#050 新建】JWT 工具类
 * ════════════════════════════════════════════════════════════
 *
 *  职责：Token 的生成、验证、解析
 *
 *  使用 jjwt 0.12.3 API（与旧版 0.9.x 写法不同，禁止混用）
 *
 *  Token Payload：{ userId: String, username: String, role: String }
 *  签名算法：HS256
 *  过期时间：由 application.properties 中 jwt.expiration 控制（默认30天）
 *
 *  【#D-04 修复】userId 在 Payload 中统一存为 String 类型。
 *  原因：jjwt 在反序列化 JSON 数值时，会根据数字大小自动选择 Integer 或 Long，
 *  导致取出类型不可预测，调用方不得不写三分支兼容代码。
 *  改为 String 后，序列化/反序列化行为完全确定：存什么取出什么。
 *
 *  向下兼容说明：
 *  旧 token（userId 为数字）经过 Long.parseLong(val.toString()) 仍可正确解析，
 *  用户无需重新登录，存量 token 在有效期内继续有效。
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    /** 获取 HS256 签名密钥 */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 JWT Token
     * @param userId   用户数据库 ID
     * @param username 用户名
     * @param role     角色（DELEGATE / LEADER / ADMIN）
     * @return 签名后的 JWT 字符串
     */
    public String generateToken(Long userId, String username, String role) {
        return Jwts.builder()
                // 【#D-04】userId 存为 String，消除 jjwt 反序列化时 Integer/Long 类型不确定的问题。
                // 取出时统一用 Long.parseLong()，行为完全可预测。
                // 向下兼容：旧 token 里的数字经 val.toString() 后 parseLong 仍能正确解析。
                .claim("userId",   userId.toString())
                .claim("username", username)
                .claim("role",     role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 验证 Token 是否有效（签名正确且未过期）
     * @return true = 有效；false = 无效/过期/篡改
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 从 Token 中提取 Claims（调用前须先 validateToken）
     */
    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 Token 提取 userId
     *
     * 【#D-04 修复】去掉原来的三分支类型判断。
     * 原因：generateToken() 现在将 userId 存为 String，
     * 取出时永远是 String，直接 parseLong 即可。
     *
     * 向下兼容：旧 token 里 userId 可能是 Integer 或 Long，
     * val.toString() 对三种类型都能正确返回纯数字字符串（如 "42"），
     * Long.parseLong 统一解析，不再需要 instanceof 分支。
     */
    public Long extractUserId(String token) {
        Object val = extractClaims(token).get("userId");
        // 无论 val 是 String("42")、Integer(42) 还是 Long(42L)，
        // toString() 都返回 "42"，parseLong 统一转换，消除类型歧义。
        return Long.parseLong(val.toString());
    }

    /** 从 Token 提取 role */
    public String extractRole(String token) {
        return (String) extractClaims(token).get("role");
    }

    /** 从 Token 提取 username */
    public String extractUsername(String token) {
        return (String) extractClaims(token).get("username");
    }

    /**
     * 从 HTTP 请求头 "Authorization: Bearer xxx" 中提取 token 字符串
     * 若格式不符则返回 null
     */
    public String extractTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        return null;
    }
}