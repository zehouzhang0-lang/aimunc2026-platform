package com.example.aimunc2026.filter;

import com.example.aimunc2026.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * ════════════════════════════════════════════════════════════
 *  【#050 新建】JWT 请求过滤器
 * ════════════════════════════════════════════════════════════
 *
 *  每次请求执行一次（OncePerRequestFilter）。
 *  若请求头携带合法 JWT，解析后将 { userId, role } 注入 SecurityContext，
 *  供 Controller 层直接读取，无需再查数据库验证身份。
 *
 *  ⚠️ Filter 不拦截/拒绝无 token 的请求（公开接口仍可访问），
 *  只是当 token 存在时解析并设置身份。鉴权决策在 Controller 层完成。
 */
@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        String token = jwtUtil.extractTokenFromHeader(authHeader);

        if (token != null && jwtUtil.validateToken(token)) {
            Claims claims = jwtUtil.extractClaims(token);
            String role   = (String) claims.get("role");

            // 【#D-04 修复】userId 在新 token 中固定为 String 类型；
            // 旧 token 中可能为 Integer 或 Long。
            // 三种类型 toString() 均返回纯数字字符串，parseLong 统一处理，
            // 不再需要 instanceof 分支。
            Long userId = Long.parseLong(claims.get("userId").toString());

            // 将 userId / role 存入 request attribute，供 Controller 直接读取
            request.setAttribute("jwtUserId", userId);
            request.setAttribute("jwtRole",   role);

            // 注入 Spring Security 上下文
            var auth = new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}