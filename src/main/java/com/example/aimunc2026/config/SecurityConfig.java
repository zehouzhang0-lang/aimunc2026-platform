package com.example.aimunc2026.config;

import com.example.aimunc2026.filter.JwtFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全配置类
 * 作用：1. 提供加密器 Bean  2. 解锁接口访问权限  3.【#050】注入JWT过滤器
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // 【#050 新增】注入JWT过滤器
    @Autowired
    private JwtFilter jwtFilter;

    // 【核心】注入加密器，后续 UserController 注册时会用到
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 【核心】配置权限过滤链
    // 继续 permitAll（鉴权逻辑在 Controller 层），保持简单
    // 【#050】在 UsernamePasswordAuthenticationFilter 之前插入 JWT 解析过滤器
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // 必须禁用 CSRF，否则前端 POST 请求会失败
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll() // 允许所有访问，配合 Vibe 模式快速调试
                )
                // 【#050】在 UsernamePasswordAuthenticationFilter 之前插入 JWT 解析过滤器
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}