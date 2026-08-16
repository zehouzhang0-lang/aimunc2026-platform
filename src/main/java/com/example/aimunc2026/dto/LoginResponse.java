package com.example.aimunc2026.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 【DTO】登录响应体
 *
 * 【本次修复】Jackson 序列化 boolean 字段的陷阱
 *
 * 问题根源：
 *   Java 字段 `boolean isNewUser`，getter 名为 `isNewUser()`。
 *   Jackson 看到 isXxx() 格式的 getter，会自动剥除 "is" 前缀，
 *   将 JSON key 序列化为 "newUser" 而不是 "isNewUser"。
 *   前端 result.isNewUser 永远是 undefined，拦截条件永远是 false。
 *
 * 修复方案：
 *   在 getter 上加 @JsonProperty("isNewUser")，强制指定 JSON key 名，
 *   覆盖 Jackson 的默认命名行为。
 *   前端 result.isNewUser 现在能正确拿到 true / false。
 *
 * 【#050 改动】新增 token 字段，用于返回 JWT token 字符串。
 */
public class LoginResponse {

    private Long   id;
    private String username;
    private String role;
    private Long   delegationId;
    private String inviteCode;
    private boolean isNewUser;
    private String token;   // 【#050 新增】JWT token，登录成功后颁发

    public LoginResponse() {}

    /**
     * 【#050 改动】构造函数末尾新增 token 参数
     */
    public LoginResponse(Long id, String username, String role,
                         Long delegationId, String inviteCode,
                         boolean isNewUser, String token) {
        this.id           = id;
        this.username     = username;
        this.role         = role;
        this.delegationId = delegationId;
        this.inviteCode   = inviteCode;
        this.isNewUser    = isNewUser;
        this.token        = token;
    }

    public Long   getId()       { return id; }
    public void   setId(Long id){ this.id = id; }

    public String getUsername()              { return username; }
    public void   setUsername(String v)      { this.username = v; }

    public String getRole()                  { return role; }
    public void   setRole(String v)          { this.role = v; }

    public Long   getDelegationId()          { return delegationId; }
    public void   setDelegationId(Long v)    { this.delegationId = v; }

    public String getInviteCode()            { return inviteCode; }
    public void   setInviteCode(String v)    { this.inviteCode = v; }

    /**
     * @JsonProperty("isNewUser") 强制 Jackson 用 "isNewUser" 作为 JSON key，
     * 而不是默认的 "newUser"（剥除 is 前缀的结果）。
     */
    @JsonProperty("isNewUser")
    public boolean isNewUser()               { return isNewUser; }
    public void    setNewUser(boolean v)     { this.isNewUser = v; }

    // 【#050 新增】JWT token getter/setter
    public String getToken()           { return token; }
    public void   setToken(String v)   { this.token = v; }
}